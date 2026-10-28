package app.linkphone.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.linkphone.core.prefs.LinkPrefs
import app.linkphone.core.util.LinkStatus

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val prefs = LinkPrefs(context)
        if (!prefs.isPaired || !prefs.autoConnect) return
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, LinkConnectionService::class.java)
                    .setAction(LinkConnectionService.ACTION_CONNECT),
            )
        }.onFailure {
            LinkStatus.log("Boot", "Android did not allow the background service to start")
        }
    }
}
