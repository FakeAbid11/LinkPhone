package dev.linkphone.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class LinkService : Service() {

    private var batteryMonitor: BatteryMonitor? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Text last posted, so a reconnect backoff loop does not re-notify for no visible change. */
    private var lastText: String? = null

    override fun onCreate() {
        super.onCreate()
        LinkCore.init(this)
        NotificationRelay.ensureBound(this)
        SystemCallWatcher.start(this)
        createChannel()

        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val initial = LinkCore.requireClient().status.value
        // Seed the dedupe with the text startForeground is about to post, so collection does not
        // immediately re-notify for the state that is already showing.
        lastText = statusText(initial)

        try {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(initial, tapIntent),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } catch (error: RuntimeException) {
            Log.w("LinkPhone", "could not start foreground service", error)
            stopSelf()
            return
        }

        val store = LinkCore.requireStore()
        val client = LinkCore.requireClient()
        observeStatus(client, tapIntent)
        batteryMonitor = BatteryMonitor(this) { battery ->
            client.sendBatteryStatus(battery.level, battery.charging)
        }.also { it.start() }
        if (store.hasPairing() && client.status.value.state == LinkState.IDLE) {
            client.connectSaved()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val store = LinkCore.requireStore()
        val client = LinkCore.requireClient()
        if (store.hasPairing() && client.status.value.state == LinkState.IDLE) {
            client.connectSaved()
        }
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w("LinkPhone", "foreground service timed out (type $fgsType)")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        CameraHub.stop("disconnected")
        CallHub.restoreMute()
        SystemCallWatcher.stop()
        batteryMonitor?.stop()
        batteryMonitor = null
        super.onDestroy()
    }

    /**
     * The notification used to say "Connected to your PC" for as long as the service lived, no
     * matter what the link was doing, while the app screen showed the real state. Follow the same
     * LinkClient status the UI follows.
     */
    private fun observeStatus(client: LinkClient, tapIntent: PendingIntent) {
        scope.launch {
            client.status.collect { status -> showStatus(status, tapIntent) }
        }
    }

    /**
     * Posts the new state, skipping the redundant re-post when nothing visible changed.
     *
     * Updates go through startForeground again rather than NotificationManager.notify: the
     * foreground-service notification is exempt from POST_NOTIFICATIONS on API 33+, but a notify()
     * with the same id is not, so on a phone where the user denied the permission the text would
     * silently freeze at whatever it was when the service started.
     */
    private fun showStatus(status: LinkStatus, tapIntent: PendingIntent) {
        val text = statusText(status)
        if (text == lastText) return
        lastText = text
        runCatching {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(status, tapIntent),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        }.onFailure { Log.w("LinkPhone", "could not update link notification", it) }
    }

    private fun buildNotification(status: LinkStatus, tapIntent: PendingIntent): Notification {
        val text = statusText(status)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setColor(getColor(R.color.notification_accent))
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun statusText(status: LinkStatus): String {
        val name = status.serverName.ifBlank {
            LinkCore.requireStore().serverName.orEmpty().ifBlank {
                getString(R.string.service_pc_generic)
            }
        }
        return when (status.state) {
            LinkState.CONNECTED -> getString(R.string.service_connected, name)
            LinkState.CONNECTING -> getString(R.string.service_connecting, name)
            LinkState.PAIRING -> getString(R.string.service_pairing)
            LinkState.ERROR ->
                if (LinkCore.requireStore().hasPairing()) {
                    getString(R.string.service_offline, name)
                } else {
                    // Nothing to search for yet, so do not promise a search.
                    getString(R.string.service_offline_unpaired)
                }
            LinkState.IDLE ->
                if (LinkCore.requireStore().hasPairing()) {
                    getString(R.string.service_idle_paired)
                } else {
                    getString(R.string.service_idle_unpaired)
                }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.service_text)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "linkphone_connection"
        private const val NOTIFICATION_ID = 41
    }
}
