package id.tipime.tipistream.extension

import android.widget.ImageView
import androidx.databinding.BindingAdapter
import coil.load
import id.tipime.tipistream.R

/**
 * Loads a channel logo (tvg-logo url from the playlist) into the ImageView, falling back to the
 * TipiStream logo when the url is missing or fails to load. Coil handles memory/disk caching so
 * scrolling the channel grid stays smooth.
 */
@BindingAdapter("logoUrl")
fun ImageView.setLogoUrl(url: String?) {
    val fallback = R.drawable.logo_tipistream
    if (url.isNullOrBlank()) {
        setImageResource(fallback)
        return
    }
    load(url) {
        placeholder(fallback)
        error(fallback)
        crossfade(true)
    }
}
