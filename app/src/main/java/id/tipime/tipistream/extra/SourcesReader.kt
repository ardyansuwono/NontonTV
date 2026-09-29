package id.tipime.tipistream.extra

import id.tipime.tipistream.extension.*
import id.tipime.tipistream.model.Playlist
import id.tipime.tipistream.model.Source
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import id.tipime.tipistream.App.Companion.runOnUiThread


class SourcesReader {
    private var sources: ArrayList<Source> = ArrayList()
    private var result: Result? = null

    interface Result {
        fun onProgress(source: String) {}
        fun onResponse(playlist: Playlist?) {}
        fun onError(source: String, error: String) {}
        fun onFinish() {}
    }

    fun set(sources: ArrayList<Source>?, result: Result): SourcesReader {
        sources?.let { this.sources.addAll(it) }
        this.result = result
        return this
    }

    fun process(useCache: Boolean) {
        // send onFinish when sources is empty
        if (sources.isEmpty()) {
            result?.onFinish(); return
        }

        // get first source & remove it
        val source = sources.first()
        sources.remove(source)

        // skip if not active
        if (!source.active) {
            process(useCache); return
        }

        // report progress
        result?.onProgress(source.path)

        // read local playlist (file path or content:// uri)
        if (!source.path.isLinkUrl()) {
            val playlist = PlaylistHelper().readSource(source.path)
            result?.onResponse(playlist)
            process(useCache); return
        }

        // get playlist from internet
        HttpClient(useCache)
            .create(source.path.toRequest())
            .enqueue(object: Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        result?.onError(source.path, e.message.toString())
                        process(useCache)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val content = response.content()
                    runOnUiThread {
                        if (response.isSuccessful) {
                            if (!content.isNullOrBlank()) {
                                val playlist = content.toPlaylist()
                                if (!playlist.isCategoriesEmpty()) result?.onResponse(playlist)
                                else result?.onError(source.path, "parse error?")
                            } else result?.onError(source.path, "null content")
                        } else result?.onError(source.path, response.message)
                        // repeat until sources is empty
                        process(useCache)
                    }
                }
            })
    }
}