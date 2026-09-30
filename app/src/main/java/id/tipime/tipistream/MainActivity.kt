package id.tipime.tipistream

import android.annotation.SuppressLint
import android.content.*
import android.content.pm.ActivityInfo
import android.os.*
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import id.tipime.tipistream.adapter.CategoryNavAdapter
import id.tipime.tipistream.adapter.ChannelAdapter
import id.tipime.tipistream.databinding.ActivityMainBinding
import id.tipime.tipistream.dialog.SearchDialog
import id.tipime.tipistream.dialog.SettingDialog
import id.tipime.tipistream.extension.*
import id.tipime.tipistream.extra.*
import id.tipime.tipistream.model.*
import kotlin.math.max

open class MainActivity : AppCompatActivity() {
    private var doubleBackToExitPressedOnce = false
    private var isTelevision = UiMode().isTelevision()
    private val preferences = Preferences()
    private val helper = PlaylistHelper()
    private lateinit var binding: ActivityMainBinding
    private var navAdapter: CategoryNavAdapter? = null
    private var channelAdapter: ChannelAdapter? = null
    private var categories: ArrayList<Category>? = null
    private var selectedIndex = 0

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            when(intent.getStringExtra(MAIN_CALLBACK)) {
                UPDATE_PLAYLIST -> updatePlaylist(false)
                INSERT_FAVORITE -> onFavoriteInserted()
                REMOVE_FAVORITE -> onFavoriteRemoved()
            }
        }
    }

    companion object {
        const val MAIN_CALLBACK = "MAIN_CALLBACK"
        const val UPDATE_PLAYLIST = "UPDATE_PLAYLIST"
        const val INSERT_FAVORITE = "REFRESH_FAVORITE"
        const val REMOVE_FAVORITE = "REMOVE_FAVORITE"
    }

    @SuppressLint("DefaultLocale")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // set button click listner
        binding.buttonSearch.setOnClickListener{ openSearch() }
        binding.buttonRefresh.setOnClickListener { updatePlaylist(false) }
        binding.buttonSettings.setOnClickListener{ openSettings() }
        binding.buttonExit.setOnClickListener { finish() }

        // local broadcast receiver to update playlist
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, IntentFilter(MAIN_CALLBACK))

        // set playlist
        if (!Playlist.cached.isCategoriesEmpty()) setPlaylistToAdapter(Playlist.cached)
        else showAlertPlaylistError(getString(R.string.null_playlist))

    }

    private fun setLoadingPlaylist(show: Boolean) {
        /* i don't why hideShimmer() leaves drawable visible */
        if (show) {
            binding.loading.startShimmer()
            binding.loading.visibility = View.VISIBLE
        }
        else {
            binding.loading.stopShimmer()
            binding.loading.visibility = View.GONE
        }
    }

    private fun setPlaylistToAdapter(playlistSet: Playlist) {
        // sort category by name
        if(preferences.sortCategory) playlistSet.sortCategories()
        // sort channels by name
        if(preferences.sortChannel) playlistSet.sortChannels()
        // remove channels with empty streamurl
        playlistSet.trimChannelWithEmptyStreamUrl()

        // favorites part
        val fav = helper.readFavorites()
            .trimNotExistFrom(playlistSet)
        if (preferences.sortFavorite) fav.sort()
        if (fav?.channels?.isNotEmpty() == true)
            playlistSet.insertFavorite(fav.channels)
        else playlistSet.removeFavorite()

        // set new playlist
        categories = playlistSet.categories
        navAdapter = CategoryNavAdapter(playlistSet.categories) { index -> selectCategory(index) }
        binding.rvCategoryNav.adapter = navAdapter

        // running-text banner from the playlist message
        setBanner(playlistSet.message)

        // write cache
        Playlist.cached = playlistSet
        helper.writeCache(playlistSet)

        // hide loading
        setLoadingPlaylist(false)
        Toast.makeText(applicationContext, R.string.playlist_updated, Toast.LENGTH_SHORT).show()

        // show first category and focus the navigation menu
        selectCategory(0)
        binding.rvCategoryNav.requestFocus()

        // launch player if playlastwatched is true
        if (preferences.playLastWatched && PlayerActivity.isFirst) {
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra(PlayData.VALUE, preferences.watched)
            this.startActivity(intent)
        }
    }

    private fun selectCategory(index: Int) {
        val cats = categories ?: return
        if (cats.isEmpty()) return
        val idx = index.coerceIn(0, cats.size - 1)
        selectedIndex = idx

        val category = cats[idx]
        val isFav = category.isFavorite() && idx == 0
        channelAdapter = ChannelAdapter(category.channels, idx, isFav)
        binding.rvChannels.layoutManager = GridLayoutManager(this, channelSpanCount())
        binding.rvChannels.adapter = channelAdapter
        binding.rvChannels.scrollToPosition(0)
        binding.textCurrentCategory.text = category.name
        navAdapter?.setSelected(idx)
    }

    private fun setBanner(message: String?) {
        if (message.isNullOrBlank()) {
            binding.banner.visibility = View.GONE
            binding.banner.text = ""
            return
        }
        // pad the ends so the looping marquee keeps a gap between repeats
        binding.banner.text = "    $message    "
        binding.banner.visibility = View.VISIBLE
        binding.banner.isSelected = true // required for the marquee to animate without focus
    }

    private fun channelSpanCount(): Int {
        val dm = resources.displayMetrics
        val sidebar = resources.getDimension(R.dimen.sidebar_width)
        val horizontalPadding = 28 * dm.density // content start + end padding
        val paneWidth = dm.widthPixels - sidebar - horizontalPadding
        val cell = resources.getDimension(R.dimen.btn_channel_width) +
                (2 * resources.getDimension(R.dimen.channel_grid_spacing))
        return max(2, (paneWidth / cell).toInt())
    }

    private fun onFavoriteInserted() {
        val cats = categories ?: return
        if (cats.isEmpty()) return
        val fav = Playlist.favorites
        if (preferences.sortFavorite) fav.sort()
        if (cats[0].isFavorite()) {
            cats[0].channels = fav.channels
            navAdapter?.notifyItemChanged(0)
            if (selectedIndex == 0) selectCategory(0)
        } else {
            cats.addFavorite(fav.channels)
            navAdapter?.notifyItemInserted(0)
            // every category shifted down by one, keep showing the current one
            selectCategory(selectedIndex + 1)
        }
    }

    private fun onFavoriteRemoved() {
        val cats = categories ?: return
        if (cats.isNotEmpty() && cats[0].isFavorite()) {
            cats.removeAt(0)
            navAdapter?.notifyItemRemoved(0)
            selectCategory((selectedIndex - 1).coerceAtLeast(0))
        }
    }

    private fun updatePlaylist(useCache: Boolean) {
        // show loading
        setLoadingPlaylist(true)

        // clearing adapter
        navAdapter?.clear()
        binding.rvChannels.adapter = null
        binding.textCurrentCategory.text = ""
        setBanner(null)
        val playlistSet = Playlist()

        SourcesReader().set(preferences.sources, object: SourcesReader.Result {
            override fun onError(source: String, error: String) {
                val snackbar = Snackbar.make(binding.root, "[${error.uppercase()}] $source", Snackbar.LENGTH_INDEFINITE)
                snackbar.setAction(android.R.string.ok) { snackbar.dismiss() }
                snackbar.show()
            }

            override fun onResponse(playlist: Playlist?) {
                // merge into playlistset
                if (playlist != null) playlistSet.mergeWith(playlist)
                else Toast.makeText(applicationContext, R.string.playlist_cant_be_parsed, Toast.LENGTH_SHORT).show()
            }

            override fun onFinish() {
                if (!playlistSet.isCategoriesEmpty()) setPlaylistToAdapter(playlistSet)
                else showAlertPlaylistError(getString(R.string.null_playlist))
            }
        }).process(useCache)
    }

    private fun showAlertPlaylistError(message: String?) {
        val alert = AlertDialog.Builder(this).apply {
            setTitle(R.string.alert_title_playlist_error)
            setMessage(message)
            setCancelable(false)
            setNeutralButton(R.string.settings) { _,_ -> openSettings() }
            setPositiveButton(R.string.dialog_retry) { _,_ -> updatePlaylist(true) }
        }
        val cache = helper.readCache()
        if (cache != null) {
            alert.setNegativeButton(R.string.dialog_cached) { _,_ -> setPlaylistToAdapter(cache) }
        }
        alert.create().show()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.setFullScreenFlags()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when(keyCode) {
            KeyEvent.KEYCODE_MENU -> openSettings()
            else -> return super.onKeyUp(keyCode, event)
        }
        return true
    }

    override fun onBackPressed() {
        if (isTelevision || doubleBackToExitPressedOnce) {
            super.onBackPressed()
            finish()
            return
        }
        doubleBackToExitPressedOnce = true
        Toast.makeText(this, getString(R.string.press_back_twice_exit_app), Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

    private fun openSettings(){
        SettingDialog().show(supportFragmentManager.beginTransaction(),null)
    }

    private fun openSearch() {
        SearchDialog().show(supportFragmentManager.beginTransaction(),null)
    }
}