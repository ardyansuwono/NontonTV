package id.tipime.tipistream.extra

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import id.tipime.tipistream.App
import id.tipime.tipistream.R
import id.tipime.tipistream.model.TrakteerRunningText
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * Streaming-overlay client for the Trakteer "running-text" widget.
 *
 * Trakteer's overlay is a thin websocket client on top of a REST state endpoint:
 *  - connect to wss://ws.trakteer.id/ws?token=<token> to be notified of changes, and
 *  - on every notification (and on open) re-fetch GET /api/overlay/running-text to get the
 *    actual queue — exactly what the official web widget does.
 *
 * The `token` in the widget URL (the `trv2-…` value) is also the `stream_key` accepted by REST.
 *
 * Messages are reported to the UI as a list of formatted strings, plus a flag telling it to
 * hide the banner (the creator toggled "hide" from their dashboard).
 */
class TrakteerClient(
    private val token: String,
    private val onMessages: (messages: List<String>, hidden: Boolean) -> Unit,
    private val onError: (message: String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .sslSocketFactory(App.sslSocketFactory, HttpsTrustManager())
        .build()

    private var socket: WebSocket? = null
    private var connected = false

    // reconnect bookkeeping
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var attempt = 0
    private var stopped = false

    /** Opens the socket. Safe to call once. */
    fun start() {
        if (connected || stopped) return
        val request = Request.Builder()
            .url("${WS_URL}/ws?token=$token")
            .build()
        socket = httpClient.newWebSocket(request, SocketListener())
    }

    /** Disconnects permanently and releases the socket. */
    fun stop() {
        stopped = true
        reconnectHandler.removeCallbacksAndMessages(null)
        socket?.close(NORMAL_CLOSURE, "stopped")
        socket = null
        connected = false
    }

    private inner class SocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "ws connected")
            connected = true
            attempt = 0
            // the server greets us with the current state; refresh anyway to be sure
            refresh()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            // we only care that the queue may have changed; pull the latest list from REST
            refresh()
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = refresh()

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "ws closed: $code")
            connected = false
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "ws failure: ${t.message}")
            connected = false
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (stopped || connected) return
        attempt++
        // A bad token is rejected on every retry, so cap the attempts and tell the user
        // instead of hammering the server forever.
        if (attempt > MAX_ATTEMPTS) {
            Log.w(TAG, "giving up after $attempt attempts")
            mainHandler.post { onError(App.context.getString(R.string.trakteer_invalid_token)) }
            return
        }
        val delay = (3000L * attempt).coerceAtMost(30_000L)
        Log.d(TAG, "reconnect attempt $attempt in ${delay}ms")
        reconnectHandler.postDelayed({ start() }, delay)
    }

    /** Fetches the running-text queue and reports it on the main thread. */
    private fun refresh() {
        val request = Request.Builder()
            .url("https://$WS_HOST/api/overlay/running-text?stream_key=$token")
            .get()
            .build()
        httpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: Response) {
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "rest ${response.code}: ${body.take(120)}")
                    return
                }
                runCatching {
                    val state = Gson().fromJson(body, TrakteerRunningText::class.java) ?: return
                    val hidden = state.session?.is_hidden == true
                    val items = state.items.orEmpty()
                    val messages = if (hidden) emptyList() else items.mapNotNull { it.format() }
                    mainHandler.post { onMessages(messages, hidden) }
                }.onFailure { Log.w(TAG, "parse failed: ${it.message}") }
            }

            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.w(TAG, "rest failure: ${e.message}")
            }
        })
    }

    private fun id.tipime.tipistream.model.TrakteerItem.format(): String? {
        // Mirrors the official widget: an anonymous supporter is shown as "Seseorang".
        val name = if (is_anonym || supporter_name.isNullOrBlank()) ANONYMOUS
                   else supporter_name!!.trim()
        return when {
            support_message.isNullOrBlank() -> name
            else -> "$name: \"${support_message!!.trim()}\""
        }
    }

    companion object {
        private const val TAG = "TrakteerClient"
        private const val WS_HOST = "ws.trakteer.id"
        private const val WS_URL = "wss://$WS_HOST"
        private const val NORMAL_CLOSURE = 1000
        private const val MAX_ATTEMPTS = 5
        private const val ANONYMOUS = "Seseorang"
    }
}
