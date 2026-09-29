package id.tipime.tipistream.extra

import android.app.UiModeManager
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import id.tipime.tipistream.App

class UiMode {
    private val context = App.context
    fun isTelevision() : Boolean {
        val manager = context.getSystemService(AppCompatActivity.UI_MODE_SERVICE) as UiModeManager
        return manager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
}