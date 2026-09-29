package id.tipime.tipistream.extension

import com.google.gson.Gson
import com.google.gson.JsonParseException
import id.tipime.tipistream.extra.M3uTool
import id.tipime.tipistream.model.*

fun Playlist?.sortCategories() {
    this?.categories?.sortBy { category -> category.name?.lowercase() }
}

fun Playlist?.sortChannels() {
    if (this == null) return
    for (catId in this.categories.indices) {
        this.categories[catId].channels?.sortBy { channel -> channel.name?.lowercase() }
    }
}

fun Playlist?.trimChannelWithEmptyStreamUrl() {
    if (this == null) return
    for (catId in this.categories.indices) {
        this.categories[catId].channels!!.removeAll { channel -> channel.streamUrl.isNullOrBlank() }
    }
}

fun Playlist?.mergeWith(playlist: Playlist?) {
    if (playlist == null) return
    playlist.categories.let { this?.categories?.addAll(it) }
    playlist.drmLicenses.let { this?.drmLicenses?.addAll(it) }
    // keep the first non-empty running-text message across merged sources
    if (this?.message.isNullOrBlank() && !playlist.message.isNullOrBlank())
        this?.message = playlist.message
}

fun Playlist?.insertFavorite(channels: ArrayList<Channel>) {
    if (this == null) return
    if (this.categories[0].isFavorite())
        this.categories[0].channels = channels
    else
        this.categories.addFavorite(channels)
}

fun Playlist?.removeFavorite() {
    if (this == null) return
    if (this.categories[0].isFavorite())
        this.categories.removeAt(0)
}

fun String?.toPlaylist(): Playlist? {
    // trying to parse json first
    try { return Gson().fromJson(this, Playlist::class.java) }
    catch (e: JsonParseException) { e.printStackTrace() }

    // if not json then m3u
    try { return M3uTool().parse(this) }
    catch (e: Exception) { e.printStackTrace() }

    // content cant be parsed
    return null
}

fun Playlist?.isCategoriesEmpty(): Boolean {
    if (this?.categories?.isEmpty() == true) return true
    return (this?.categories?.count() == 1) && (this.categories[0].channels?.count() == 0)
}