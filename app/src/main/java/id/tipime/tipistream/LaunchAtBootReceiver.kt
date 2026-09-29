package id.tipime.tipistream

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import id.tipime.tipistream.extra.Preferences

class LaunchAtBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val preferences = Preferences()
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && preferences.launchAtBoot) {
            val i = Intent(context, MainActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    }
}