package id.tipime.tipistream.extension

import id.tipime.tipistream.App
import id.tipime.tipistream.R
import id.tipime.tipistream.model.Category
import id.tipime.tipistream.model.Channel

fun Category?.isFavorite(): Boolean {
    return this?.name == App.context.getString(R.string.favorite_channel)
}

fun ArrayList<Category>?.addFavorite(channels: ArrayList<Channel>) {
    val title = App.context.getString(R.string.favorite_channel)
    this?.add(0, Category().apply {
        this.name = title
        this.channels = channels
    })
}