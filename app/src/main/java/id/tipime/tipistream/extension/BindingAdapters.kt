package id.tipime.tipistream.extension

import android.widget.ImageView
import androidx.databinding.BindingAdapter
import coil.load
import id.tipime.tipistream.R

/**
 * Loads a channel logo (tvg-logo url from the playlist) into the ImageView, falling back to the
 * TipiStream logo when the url is missing or fails to load. Coil handles memory/disk caching so
 * scrolling the channel grid stays smooth.
 *
 * Every rebind is routed through Coil's [load] — including the "no logo" case — so any in-flight
 * request left over from the previous channel on this recycled ImageView is cancelled. Setting the
 * fallback with setImageResource() would NOT cancel that pending request, letting a slow load from
 * the old channel land on the recycled view and show the wrong logo (the "logo keeps changing" bug).
 */
@BindingAdapter("logoUrl")
fun ImageView.setLogoUrl(url: String?) {
    val fallback = R.drawable.logo_tipistream
    val data = if (url.isNullOrBlank()) null else url
    load(data) {
        placeholder(fallback)
        error(fallback)
        fallback(fallback)
        crossfade(true)
    }
}
