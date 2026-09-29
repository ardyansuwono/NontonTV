package id.tipime.tipistream.extra

import id.tipime.tipistream.App
import okhttp3.*
import java.io.File

class HttpClient(private val useCache: Boolean) {
    fun create(request: Request): Call {
        val cacheFile = File(App.context.cacheDir, "HttpClient")
        val cacheSize = 10L * 1024L * 1024L
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .sslSocketFactory(App.sslSocketFactory, HttpsTrustManager())

        if (useCache) client.cache(Cache(cacheFile, cacheSize))
        return client.build().newCall(request)
    }
}