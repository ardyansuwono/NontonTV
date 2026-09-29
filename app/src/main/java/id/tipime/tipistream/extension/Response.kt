package id.tipime.tipistream.extension

import okhttp3.Response

fun Response.content(): String? {
    val content = this.body?.string()
    this.close()
    return content
}