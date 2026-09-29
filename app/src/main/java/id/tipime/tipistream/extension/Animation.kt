package id.tipime.tipistream.extension

import android.view.View
import android.view.animation.AnimationUtils
import id.tipime.tipistream.App
import id.tipime.tipistream.R

fun View?.startAnimation(hasFocus: Boolean) {
    val animation = AnimationUtils.loadAnimation(
        App.context, if (hasFocus) R.anim.zoom_120 else R.anim.zoom_100)
    this?.startAnimation(animation)
    animation.fillAfter = true
}