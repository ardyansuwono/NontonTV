package id.tipime.tipistream.extra

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import id.tipime.tipistream.App
import id.tipime.tipistream.model.Supporter
import id.tipime.tipistream.model.TrakteerTopSupporter
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Reads the Trakteer "running text" widget queue over its REST v2 API.
 *
 * This is the same data the official OBS widget (`stream.trakteer.id/running-text/index.html`)
 * shows. `top-supporters` returns the top donors over `interval` seconds; `latest-tips` returns
 * the most recent tips with their support messages.
 *
 * Everything is posted back to the main thread. All failures are swallowed and reported via
 * [onError] — a broken donation feed must never take the app down with it.
 */
class TrakteerClient(
    private val key: String,
    private val onMessages: (messages: List<String>) -> Unit,
    private val onError: (message: String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .sslSocketFactory(App.sslSocketFactory, HttpsTrustManager())
        .build()

    private var stopped = false
    private var call: okhttp3.Call? = null

    /**
     * Starts the running-text feed. The first fetch happens immediately; every later fetch is
     * scheduled only after the previous one finishes (on success OR failure), so requests can
     * never pile up. A slow network therefore widens the interval instead of spawning a queue.
     */
    fun start() {
        if (stopped) return
        fetch()
    }

    private fun fetch() {
        val type = TYPE
        val url = if (type == TYPE_TOP_SUPPORTERS)
            "https://$API_HOST/v2/stream/$key/top-supporters?interval=$INTERVAL&count=$COUNT"
        else
            "https://$API_HOST/v2/stream/$key/latest-tips?limit=$COUNT"
        val request = Request.Builder().url(url).get().build()
        call = httpClient.newCall(request)
        call?.enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (stopped) return
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "rest ${response.code}: ${body.take(120)}")
                    mainHandler.post { onError("HTTP ${response.code}") }
                }
                else {
                    runCatching {
                        val state = Gson().fromJson(body, TrakteerTopSupporter::class.java)
                        val unit = state?.unitName.orEmpty()
                        val messages = when (type) {
                            TYPE_TOP_SUPPORTERS -> state?.topSupporters.orEmpty()
                                .mapNotNull { it.formatTopSupporter(unit) }
                            else -> state?.latestTips.orEmpty()
                                .mapNotNull { it.formatLatestTip() }
                        }
                        Log.d(TAG, "rest ok ($type): ${messages.size} messages")
                        mainHandler.post { onMessages(messages) }
                    }.onFailure {
                        Log.w(TAG, "parse failed: ${it.message}")
                        mainHandler.post { onError(it.message ?: "parse failed") }
                    }
                }
                scheduleNext()
            }

            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (stopped) return
                Log.w(TAG, "rest failure: ${e.message}")
                mainHandler.post { onError(e.message ?: "network failure") }
                scheduleNext()
            }
        })
    }

    private fun scheduleNext() {
        if (stopped) return
        mainHandler.postDelayed({ fetch() }, POLL_INTERVAL_MS)
    }

    /** Stops the feed permanently and cancels any in-flight request. */
    fun stop() {
        stopped = true
        mainHandler.removeCallbacksAndMessages(null)
        call?.cancel()
        call = null
    }

    private fun Supporter.formatTopSupporter(unit: String): String {
        // rt_type = top-supporters → "Name (Sum Unit)" — mirrors the widget's per-item text.
        val name = (name ?: displayName)?.takeIf { it.isNotBlank() }?.trim() ?: return ""
        val formatted = java.text.NumberFormat.getIntegerInstance(java.util.Locale("id", "ID"))
            .format(sum.toLong())
        return if (unit.isBlank()) name else "$name ($formatted $unit)"
    }

    private fun Supporter.formatLatestTip(): String {
        // rt_type = latest-tips → the actual support message, like the web widget.
        val name = (name ?: displayName)?.takeIf { it.isNotBlank() }?.trim()
        val msg = supportMessage?.trim()?.takeIf { it.isNotBlank() }
        return when {
            name == null -> msg ?: ""
            msg == null -> name
            else -> "$name: \"$msg\""
        }.takeIf { it.isNotBlank() } ?: ""
    }

    companion object {
        private const val TAG = "TrakteerClient"
        private const val API_HOST = "api.trakteer.id"
        // hard-coded widget settings for the proof of concept (stage 3 moves these to Preferences)
        const val TYPE_TOP_SUPPORTERS = "top-supporters"
        const val TYPE_LATEST_TIPS = "latest-tips"
        const val TYPE = TYPE_LATEST_TIPS
        const val INTERVAL = 90   // rt_interval (seconds), only used by top-supporters
        const val COUNT = 10      // rt_count
        const val POLL_INTERVAL_MS = 30_000L  // refresh the donation feed every 30s
    }
}
