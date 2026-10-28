package app.linkphone.features.notifications

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.linkphone.core.model.NotificationDto
import app.linkphone.core.model.NotificationRemoved
import app.linkphone.core.net.ConnectionHub
import app.linkphone.core.protocol.ProtocolCodec
import app.linkphone.core.protocol.Types
import app.linkphone.core.util.LinkStatus

class LinkNotificationListener : NotificationListenerService() {
    private val labels = HashMap<String, String>()

    override fun onListenerConnected() {
        instance = this
        LinkStatus.log("Notif", "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        instance = null
        LinkStatus.log("Notif", "Notification listener disconnected")
        runCatching {
            requestRebind(ComponentName(this, LinkNotificationListener::class.java))
        }
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val status = sbn ?: return
        if (status.packageName == packageName) return
        if (!ConnectionHub.isConnected()) return
        val notification = status.notification ?: return
        val key = status.key ?: return
        val extras = notification.extras

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val lines = runCatching {
            extras.getCharSequenceArrayList(Notification.EXTRA_TEXT_LINES)
                ?.joinToString("\n") { it.toString() }
        }.getOrNull()
        val text = sequenceOf(
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText,
            lines,
        ).firstOrNull { !it.isNullOrBlank() }?.trim()

        if (title.isNullOrBlank() && text.isNullOrBlank()) return

        val dto = NotificationDto(
            key = key,
            packageName = status.packageName,
            appName = labelFor(status.packageName),
            title = title?.take(300),
            text = text?.take(2_000),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.take(300),
            category = notification.category,
            ongoing = status.isOngoing,
            time = if (status.postTime > 0) status.postTime else System.currentTimeMillis(),
        )
        ConnectionHub.sendEvent(Types.NOTIF_POST, ProtocolCodec.data(dto))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val status = sbn ?: return
        if (status.packageName == packageName) return
        val key = status.key ?: return
        ConnectionHub.sendEvent(
            Types.NOTIF_REMOVED,
            ProtocolCodec.data(NotificationRemoved(key)),
        )
    }

    private fun labelFor(pkg: String): String = synchronized(labels) {
        labels.getOrPut(pkg) {
            runCatching {
                val info = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(pkg)
        }
    }

    companion object {
        @Volatile
        private var instance: LinkNotificationListener? = null

        fun isConnected(): Boolean = instance != null

        fun dismiss(key: String): Boolean {
            val service = instance ?: return false
            return runCatching { service.cancelNotification(key) }.isSuccess
        }
    }
}
