package id.tipime.tipistream.dialog

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import id.tipime.tipistream.R
import id.tipime.tipistream.adapter.SearchAdapter
import id.tipime.tipistream.databinding.SearchDialogBinding
import id.tipime.tipistream.extension.isFavorite
import id.tipime.tipistream.extension.setFullScreenFlags
import id.tipime.tipistream.model.Channel
import id.tipime.tipistream.model.PlayData
import id.tipime.tipistream.model.Playlist

class SearchDialog : DialogFragment() {
    private var _binding : SearchDialogBinding? = null
    private val binding get() = _binding!!
    lateinit var searchAdapter: SearchAdapter

    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            val width = ViewGroup.LayoutParams.MATCH_PARENT
            val height = ViewGroup.LayoutParams.MATCH_PARENT
            //full screen
            dialog.window!!.setLayout(width, height)
            dialog.window?.setFullScreenFlags()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = AppCompatDialog(requireActivity(), R.style.SettingsDialogThemeOverlay)
        dialog.setTitle(R.string.search_channel)
        dialog.setCanceledOnTouchOutside(false)

        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SearchDialogBinding.inflate(inflater,container, false)
        val dialogView = binding.root

        val channels = ArrayList<Channel>()
        val listdata = ArrayList<PlayData>()
        val playlist = Playlist.cached
        for (catId in playlist.categories.indices) {
            val cat = playlist.categories[catId]
            if (catId == 0 && cat.isFavorite()) continue
            val ch = cat.channels ?: continue
            for (chId in ch.indices) {
                channels.add(ch[chId])
                listdata.add(PlayData(catId, chId))
            }
        }

        //recycler view
        searchAdapter = SearchAdapter(channels, listdata)
        binding.searchAdapter = searchAdapter
        binding.searchList.layoutManager = GridLayoutManager(context, spanColumn())

        //edittext
        binding.searchInput.apply {
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable) {
                    searchAdapter.filter.filter(s)
                    binding.searchList.visibility = if(s.isNotEmpty()) View.VISIBLE else View.GONE
                    binding.searchReset.visibility = if(s.isNotEmpty()) View.VISIBLE else View.GONE
                }
            })
        }

        //button cleartext
        binding.searchReset.setOnClickListener {
            binding.searchInput.text?.clear()
        }

        //button close
        binding.searchClose.setOnClickListener {
            dismiss()
        }
        return dialogView
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun spanColumn(): Int {
        val screenWidthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        return (screenWidthDp / 150 + 0.5).toInt()
    }
}
