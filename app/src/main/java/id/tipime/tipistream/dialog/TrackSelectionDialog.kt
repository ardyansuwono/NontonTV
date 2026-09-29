package id.tipime.tipistream.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.content.res.Resources
import android.os.Bundle
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.ui.TrackSelectionView
import androidx.media3.ui.TrackSelectionView.TrackSelectionListener
import com.google.android.material.tabs.TabLayout
import id.tipime.tipistream.R

/** Dialog to select tracks (migrated to AndroidX Media3).  */
@Suppress("DEPRECATION")
class TrackSelectionDialog : DialogFragment() {
    private val tabFragments: SparseArray<TrackSelectionViewFragment> = SparseArray()
    private val tabTrackTypes: ArrayList<Int> = ArrayList()
    private var titleId = 0
    private lateinit var onClickListener: DialogInterface.OnClickListener
    private lateinit var onDismissListener: DialogInterface.OnDismissListener

    init {
        // Retain instance across activity re-creation to prevent losing access to init data.
        retainInstance = true
    }

    private fun init(
        titleId: Int,
        tracks: Tracks,
        trackSelectionParameters: TrackSelectionParameters,
        allowAdaptiveSelections: Boolean,
        allowMultipleOverrides: Boolean,
        onClickListener: DialogInterface.OnClickListener,
        onDismissListener: DialogInterface.OnDismissListener
    ) {
        this.titleId = titleId
        this.onClickListener = onClickListener
        this.onDismissListener = onDismissListener

        // group Tracks.Group by track type so each supported type gets its own tab
        val groupsByType = LinkedHashMap<Int, ArrayList<Tracks.Group>>()
        for (group in tracks.groups) {
            val trackType = group.type
            if (!isSupportedTrackType(trackType) || group.length == 0) continue
            groupsByType.getOrPut(trackType) { ArrayList() }.add(group)
        }
        for ((trackType, groups) in groupsByType) {
            val overrides = HashMap<TrackGroup, TrackSelectionOverride>()
            for ((mediaTrackGroup, override) in trackSelectionParameters.overrides) {
                if (mediaTrackGroup.type == trackType) overrides[mediaTrackGroup] = override
            }
            val tabFragment = TrackSelectionViewFragment()
            tabFragment.init(
                groups,
                trackSelectionParameters.disabledTrackTypes.contains(trackType),
                overrides,
                allowAdaptiveSelections,
                allowMultipleOverrides
            )
            tabFragments.put(tabTrackTypes.size, tabFragment)
            tabTrackTypes.add(trackType)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = AppCompatDialog(requireActivity(), R.style.TrackSelectionDialogThemeOverlay)
        dialog.setTitle(titleId)
        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener.onDismiss(dialog)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val dialogView = inflater.inflate(R.layout.track_selection_dialog, container, false)
        val viewPager = dialogView.findViewById<ViewPager>(R.id.track_selection_dialog_view_pager).apply {
            adapter = FragmentAdapter(childFragmentManager)
        }
        dialogView.findViewById<TabLayout>(R.id.track_selection_dialog_tab_layout).apply {
            setupWithViewPager(viewPager)
            visibility = if (tabFragments.size() > 1) View.VISIBLE else View.GONE
        }
        dialogView.findViewById<Button>(R.id.track_selection_dialog_cancel_button).apply {
            setOnClickListener { dismiss() }
        }
        dialogView.findViewById<Button>(R.id.track_selection_dialog_ok_button).apply {
            setOnClickListener {
                onClickListener.onClick(dialog, DialogInterface.BUTTON_POSITIVE)
                dismiss()
            }
        }
        return dialogView
    }

    private inner class FragmentAdapter(fragmentManager: FragmentManager?) :
        FragmentPagerAdapter(fragmentManager!!, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getItem(position: Int): Fragment = tabFragments.valueAt(position)
        override fun getCount(): Int = tabFragments.size()
        override fun getPageTitle(position: Int): CharSequence =
            getTrackTypeString(resources, tabTrackTypes[position])
    }

    /** Fragment to show a track selection in a tab of the track selection dialog.  */
    class TrackSelectionViewFragment : Fragment(), TrackSelectionListener {
        private var trackGroups: List<Tracks.Group> = emptyList()
        private var allowAdaptiveSelections = false
        private var allowMultipleOverrides = false

        var isDisabled = false
        var overrides: Map<TrackGroup, TrackSelectionOverride> = emptyMap()

        init {
            // Retain instance across activity re-creation to prevent losing access to init data.
            retainInstance = true
        }

        fun init(
            trackGroups: List<Tracks.Group>,
            initialIsDisabled: Boolean,
            initialOverrides: Map<TrackGroup, TrackSelectionOverride>,
            allowAdaptiveSelections: Boolean,
            allowMultipleOverrides: Boolean
        ) {
            this.trackGroups = trackGroups
            this.isDisabled = initialIsDisabled
            this.overrides = initialOverrides
            this.allowAdaptiveSelections = allowAdaptiveSelections
            this.allowMultipleOverrides = allowMultipleOverrides
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val rootView = inflater.inflate(R.layout.exo_track_selection_dialog, container, false)
            val trackSelectionView: TrackSelectionView = rootView.findViewById(R.id.exo_track_selection_view)
            trackSelectionView.setShowDisableOption(true)
            trackSelectionView.setAllowMultipleOverrides(allowMultipleOverrides)
            trackSelectionView.setAllowAdaptiveSelections(allowAdaptiveSelections)
            trackSelectionView.init(trackGroups, isDisabled, overrides, null, this)
            return rootView
        }

        override fun onTrackSelectionChanged(isDisabled: Boolean, overrides: Map<TrackGroup, TrackSelectionOverride>) {
            this.isDisabled = isDisabled
            this.overrides = overrides
        }
    }

    companion object {
        /** Whether the dialog will have content if built for the given [Player]. */
        fun willHaveContent(player: Player): Boolean {
            return player.currentTracks.groups.any { isSupportedTrackType(it.type) && it.length > 0 }
        }

        /**
         * Creates a dialog for the given [Player]. The player's [TrackSelectionParameters]
         * are updated when the user confirms the selection.
         */
        fun createForPlayer(player: Player, onDismissListener: DialogInterface.OnDismissListener): TrackSelectionDialog {
            val trackSelectionDialog = TrackSelectionDialog()
            trackSelectionDialog.init(
                R.string.track_selection_title,
                player.currentTracks,
                player.trackSelectionParameters,
                allowAdaptiveSelections = true,
                allowMultipleOverrides = false,
                onClickListener = { _: DialogInterface?, _: Int ->
                    val builder = player.trackSelectionParameters.buildUpon()
                    for (i in 0 until trackSelectionDialog.tabTrackTypes.size) {
                        val trackType = trackSelectionDialog.tabTrackTypes[i]
                        val fragment = trackSelectionDialog.tabFragments.valueAt(i)
                        builder.setTrackTypeDisabled(trackType, fragment.isDisabled)
                        builder.clearOverridesOfType(trackType)
                        for (override in fragment.overrides.values) {
                            builder.addOverride(override)
                        }
                    }
                    player.trackSelectionParameters = builder.build()
                },
                onDismissListener = onDismissListener
            )
            return trackSelectionDialog
        }

        private fun isSupportedTrackType(trackType: Int): Boolean {
            return when (trackType) {
                C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT -> true
                else -> false
            }
        }

        private fun getTrackTypeString(resources: Resources, trackType: Int): String {
            return when (trackType) {
                C.TRACK_TYPE_VIDEO -> resources.getString(R.string.track_type_video)
                C.TRACK_TYPE_AUDIO -> resources.getString(R.string.track_type_audio)
                C.TRACK_TYPE_TEXT -> resources.getString(R.string.track_type_text)
                else -> throw IllegalArgumentException()
            }
        }
    }
}
