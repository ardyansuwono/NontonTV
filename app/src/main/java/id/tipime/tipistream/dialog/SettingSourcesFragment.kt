package id.tipime.tipistream.dialog

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import id.tipime.tipistream.R
import id.tipime.tipistream.adapter.SourcesAdapter
import id.tipime.tipistream.databinding.SettingSourcesFragmentBinding
import id.tipime.tipistream.extension.isLinkUrl
import id.tipime.tipistream.extra.SourceChecker
import id.tipime.tipistream.model.Source

class SettingSourcesFragment: Fragment() {
    companion object {
        var sources: ArrayList<Source>? = null
    }

    private var adapter: SourcesAdapter? = null

    // Storage Access Framework picker (scoped-storage friendly, replaces legacy FilePicker)
    private val openDocuments = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val current = adapter ?: return@registerForActivityResult
        for (uri in uris) {
            // persist read access across reboots
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { /* some providers don't grant persistable access */ }
            current.addItem(Source().apply {
                this.path = uri.toString()
                active = true
            })
        }
        sources = current.getItems()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = SettingSourcesFragmentBinding.inflate(inflater, container, false)

        val adapter = SourcesAdapter(sources)
        this.adapter = adapter
        binding.sourcesAdapter = adapter
        binding.rvSources.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))

        binding.btnPick.setOnClickListener {
            // json / m3u playlists don't have reliable mime types across providers, so allow any file
            openDocuments.launch(arrayOf("*/*"))
        }

        val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        var clipText = clipboard.primaryClip?.getItemAt(0)?.text.toString()
        binding.inputSource.apply {
            setText(if (clipText.isLinkUrl()) clipText.trim() else "")
            setOnEditorActionListener { _, i, k ->
                if (i == EditorInfo.IME_ACTION_DONE || k.keyCode == KeyEvent.KEYCODE_ENTER) {
                    binding.btnAdd.performClick(); true
                }
                else false
            }
        }

        binding.btnAdd.setOnClickListener {
            val inputSource = binding.inputSource
            var input = inputSource.text.toString()
            if (input.isBlank()) {
                clipText = clipboard.primaryClip?.getItemAt(0)?.text.toString()
                if (clipText.isLinkUrl()) input = clipText
                else return@setOnClickListener
            }
            else if (!input.isLinkUrl()) return@setOnClickListener

            it.isEnabled = false
            inputSource.isEnabled = false
            inputSource.setText(R.string.checking_url)

            val source = Source().apply {
                path = input
                active = true
            }

            SourceChecker().set(source, object: SourceChecker.Result{
                override fun onCheckResult(result: Boolean) {
                    it.isEnabled = true
                    inputSource.text?.clear()
                    inputSource.isEnabled =true
                    if (result) {
                        adapter.addItem(source)
                        sources = adapter.getItems()
                    }
                    else {
                        inputSource.setText(input)
                        Toast.makeText(context, R.string.link_error, Toast.LENGTH_SHORT).show()
                    }
                }
            }).run()
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter = null
    }
}
