package id.tipime.tipistream.extension

import android.text.Html
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import okhttp3.Request
import java.io.File
import java.net.URLDecoder
import java.util.*
import java.util.zip.CRC32

fun String?.isLinkUrl(): Boolean {
    if (this == null) return false
    return Regex("^https?://(?:[\\w.-]+\\.[a-zA-Z]{2,6}|\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})(?::\\d+)?(?:/.*)?\$")
        .matches(this)
}

fun String?.isStreamUrl(): Boolean {
    if (this == null) return false
    return Regex("^(?:https?|rt[m|s]?p)://(?:[\\w.-]+\\.[a-zA-Z]{2,6}|\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})(?::\\d+)?(?:/.*)?\$")
        .matches(this)
}

fun String?.isPathExist(): Boolean {
    return File(this.toString()).exists()
}

fun String?.toFile(): File {
    return File(this.toString())
}

fun String?.findPattern(pattern: String): String? {
    if (this == null) return null
    val result = Regex(pattern, RegexOption.IGNORE_CASE).matchEntire(this)
    return result?.groups?.get(1)?.value
}

@Suppress("DEPRECATION")
fun String?.normalize(): String? {
    if (this == null) return null
    val decoded = Html.fromHtml(this).toString()
    return Regex("([~@#\$%&<>{}();_=])(?:\\1{2,})").replace(decoded, "").trim()
}

fun String.toRequest(): Request {
    return Request.Builder().url(this).build()
}

fun String.toRequestBuilder(): Request.Builder {
    return Request.Builder().url(this)
}

fun String.decodeUrl(): String {
    return URLDecoder.decode(this, "utf-8")
}

fun String.decodeHex(): ByteArray {
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

fun String.toClearKey(): ByteArray {
    val raw = this.trim()
    // A KODIPROP license_key may already be a full ClearKey license JSON
    // (e.g. {"keys":[{"kty":"oct","kid":"…","k":"…"}, …],"type":"temporary"}).
    // In that case use it verbatim — trying to hex-split it on ':' throws
    // NumberFormatException in decodeHex() and force-closes the player.
    if (raw.startsWith("{")) return raw.toByteArray()
    // Otherwise it's the "kid:key" hex shorthand -> build the license JSON.
    val keyId = raw.substringBefore(":").decodeHex().toBase64Url()
    val keyValue = raw.substringAfter(":").decodeHex().toBase64Url()
    return """{"keys":[{"kty":"oct","k":"$keyValue","kid":"$keyId"}],"type":"temporary"}""".toByteArray()
}

fun String.toCRC32(): String {
    val bytes = this.toByteArray()
    return CRC32().apply { update(bytes) }.value.toString()
}

fun String.toUUID(): UUID {
    return when {
        this.contains("clearkey") -> C.CLEARKEY_UUID
        this.contains("widevine") -> C.WIDEVINE_UUID
        this.contains("playready") -> C.PLAYREADY_UUID
        else -> C.UUID_NIL
    }
}

/**
 * True when this is an http(s) url whose last path segment has no file-extension
 * (e.g. a short link ".../asian1" or a worker-proxied ".../rcti"). Such urls are ambiguous:
 * they may redirect to HLS, DASH or a progressive container, so their real type can only be
 * known by probing the endpoint (see PlayerActivity.resolveStreamMime).
 */
fun String?.isExtensionlessHttpStream(): Boolean {
    val url = this?.lowercase() ?: return false
    if (!url.startsWith("http")) return false
    val path = url.substringBefore('?').substringBefore('#')
    val lastSegment = path.substringAfterLast('/')
    return !lastSegment.contains('.')
}

/**
 * Maps an http Content-Type header to a Media3 container MIME type. Returns MimeTypes for HLS/DASH,
 * an empty string to signal "progressive container, let Media3 sniff it" (distinct from a null
 * "inconclusive/unknown, fall back to the url heuristic").
 */
fun String?.contentTypeToMime(): String? {
    val ct = this?.lowercase()?.substringBefore(';')?.trim() ?: return null
    return when {
        ct.contains("mpegurl") -> MimeTypes.APPLICATION_M3U8
        ct.contains("dash+xml") -> MimeTypes.APPLICATION_MPD
        ct.contains("smoothstreaming") -> MimeTypes.APPLICATION_SS
        // any concrete audio/video container (video/x-flv, video/mp4, video/mp2t, …) is progressive
        ct.startsWith("video/") || ct.startsWith("audio/") || ct.contains("flv") -> ""
        // text/html, application/octet-stream, application/json, … -> inconclusive
        else -> null
    }
}

/**
 * Resolves the streaming container MIME type for this url. Media3 infers the container from the
 * url file-extension, which fails for extension-less adaptive streams (e.g. ".../dashm/6299" or a
 * worker-proxied ".../rcti") with ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED. A KODIPROP
 * "manifest_type" hint takes priority, otherwise the type is guessed from the url. Returns null to
 * let Media3 auto-detect. Note extension-less urls are only a best-effort guess here; prefer
 * probing the real content type (see PlayerActivity.resolveStreamMime) when the type matters.
 */
fun String?.toStreamMimeType(manifestType: String? = null): String? {
    when (manifestType?.lowercase()) {
        "mpd", "dash" -> return MimeTypes.APPLICATION_MPD
        "hls", "m3u8" -> return MimeTypes.APPLICATION_M3U8
        "ism", "ss", "smoothstreaming" -> return MimeTypes.APPLICATION_SS
    }
    val url = this?.lowercase() ?: return null
    // inspect the path only, query strings often carry unrelated dots/extensions
    val path = url.substringBefore('?').substringBefore('#')
    val lastSegment = path.substringAfterLast('/')
    return when {
        path.contains(".mpd") || path.contains("/dash") -> MimeTypes.APPLICATION_MPD
        path.contains(".m3u8") || path.contains("/hls") -> MimeTypes.APPLICATION_M3U8
        path.contains(".ism") -> MimeTypes.APPLICATION_SS
        // known progressive/single-file containers -> let Media3 auto-detect
        Regex("\\.(mp4|m4v|mkv|webm|ts|mp3|aac|flv|mov|3gp|ogg|opus|wav)\$")
            .containsMatchIn(lastSegment) -> null
        // rtmp/rtsp have their own media sources, don't force a mime
        url.startsWith("rtmp") || url.startsWith("rtsp") -> null
        // extension-less http(s) endpoint (e.g. ".../rcti") -> default to HLS,
        // by far the most common container for extension-less live streams
        url.startsWith("http") && !lastSegment.contains('.') -> MimeTypes.APPLICATION_M3U8
        else -> null
    }
}
