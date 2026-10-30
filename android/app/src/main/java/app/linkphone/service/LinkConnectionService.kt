package app.linkphone.service

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.linkphone.BuildConfig
import app.linkphone.MainActivity
import app.linkphone.R
import app.linkphone.core.crypto.DeviceIdentity
import app.linkphone.core.crypto.normalizeFingerprint
import app.linkphone.core.model.BatteryStatusWire
import app.linkphone.core.model.CallDialRequest
import app.linkphone.core.model.CallSpeakerRequest
import app.linkphone.core.model.CallState
import app.linkphone.core.model.ContactListRequest
import app.linkphone.core.model.ContactListResult
import app.linkphone.core.model.ClipboardSetRequest
import app.linkphone.core.model.HandoffOpenRequest
import app.linkphone.core.model.MirrorStartRequest
import app.linkphone.core.model.MirrorState
import app.linkphone.core.model.MirrorTouchRequest
import app.linkphone.core.model.NotificationDismissRequest
import app.linkphone.core.model.NotificationListResult
import app.linkphone.core.model.OkResult
import app.linkphone.core.model.PhotoAlbumListResult
import app.linkphone.core.model.PhotoFetchRequest
import app.linkphone.core.model.PhotoListRequest
import app.linkphone.core.model.PhotoListResult
import app.linkphone.core.model.PhotoStreamMeta
import app.linkphone.core.model.Pong
import app.linkphone.core.model.SmsChanged
import app.linkphone.core.model.SmsListRequest
import app.linkphone.core.model.SmsListResult
import app.linkphone.core.model.SmsOpenThreadRequest
import app.linkphone.core.model.SmsSendRequest
import app.linkphone.core.model.StreamEnd
import app.linkphone.core.net.ConnectionHub
import app.linkphone.core.net.LinkAuthException
import app.linkphone.core.net.LinkProtocolException
import app.linkphone.core.net.PcDiscovery
import app.linkphone.core.net.PhoneConnector
import app.linkphone.core.net.PhoneSession
import app.linkphone.core.prefs.LinkPrefs
import app.linkphone.core.protocol.BinaryCodec
import app.linkphone.core.protocol.Envelope
import app.linkphone.core.protocol.ErrorCodes
import app.linkphone.core.protocol.ProtocolCodec
import app.linkphone.core.protocol.Types
import app.linkphone.core.protocol.decodeData
import app.linkphone.core.util.Backoff
import app.linkphone.core.util.FeaturePermissionException
import app.linkphone.core.util.LinkState
import app.linkphone.core.util.LinkStatus
import app.linkphone.core.util.SafeInput
import app.linkphone.features.BatteryReader
import app.linkphone.features.BatteryStatus
import app.linkphone.features.Permissions
import app.linkphone.features.PhoneInfoBuilder
import app.linkphone.features.calls.CallController
import app.linkphone.features.clipboard.ClipboardBridge
import app.linkphone.features.contacts.ContactRepository
import app.linkphone.features.mirror.LinkAccessibilityService
import app.linkphone.features.mirror.MirrorCodecChoice
import app.linkphone.features.mirror.MirrorController
import app.linkphone.features.notifications.LinkNotificationListener
import app.linkphone.features.photos.PhotoRepository
import app.linkphone.features.sms.SmsObserver
import app.linkphone.features.sms.SmsPayload
import app.linkphone.features.sms.SmsReader
import app.linkphone.features.sms.SmsSender
import app.linkphone.ui.ClipboardApplyActivity
import app.linkphone.ui.MirrorConsentActivity
import app.linkphone.ui.SendClipboardActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLHandshakeException
import kotlin.math.min

class LinkConnectionService : Service() {

    private lateinit var prefs: LinkPrefs
    private val identity = DeviceIdentity()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val reconnectSignal = Channel<Unit>(Channel.CONFLATED)
    private val streamCounter = AtomicLong(1)
    private val photoGate = java.util.concurrent.Semaphore(2)

    /** Bounds how many PC commands run at once; a flood would starve the pool. */
    private val commandGate = Semaphore(4)

    private var loopJob: Job? = null
    private var currentConnector: PhoneConnector? = null
    private var currentSession: PhoneSession? = null
    private var smsObserver: SmsObserver? = null
    private var smsDebounceJob: Job? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var reconnectWakeLock: PowerManager.WakeLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var lastBatteryLevel = -1
    private var lastBatteryCharging = false
    private var destroyed = false
    private var explicitStop = false
    private var lastStartId = 0

    private val smsReader by lazy { SmsReader(this) }
    private val smsSender by lazy { SmsSender(this) }
    private val contactRepository by lazy { ContactRepository(this) }
    private val photos by lazy { PhotoRepository(this) }
    private val calls by lazy { CallController(this, scope) { state -> sendCallState(state) } }
    private val mirror by lazy {
        MirrorController(
            context = this,
            onFrame = ::sendMirrorFrame,
            onState = ::sendMirrorState,
            onStopped = ::onMirrorStopped,
        )
    }

    override fun onCreate() {
        super.onCreate()
        prefs = LinkPrefs(this)
        createChannels()
        acquireLocks()
        registerNetworkCallback()
        registerBatteryReceiver()
        if (!Permissions.ignoringBatteryOptimizations(this)) {
            LinkStatus.log(
                "Service",
                "Battery optimization is on; Android may restrict background networking",
            )
        }
        LinkStatus.log("Service", "Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (!startForegroundCompat(includeMediaProjection = mirror.isActive())) {
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_CONNECT) {
            prefs.desiredConnected = true
        }

        when (intent?.action) {
            ACTION_DISCONNECT -> {
                explicitStop = true
                prefs.desiredConnected = false
                LinkStatus.setState(LinkState.Stopped)
                shutdownConnection()
                stopSelfResult(startId)
                return START_NOT_STICKY
            }

            ACTION_FORGET -> {
                explicitStop = true
                prefs.desiredConnected = false
                prefs.clearPairing()
                LinkStatus.setState(LinkState.Stopped)
                shutdownConnection()
                stopSelfResult(startId)
                return START_NOT_STICKY
            }

            ACTION_MIRROR_START -> handleMirrorStart(intent)
            ACTION_MIRROR_CANCEL -> {
                cancelAlert(MIRROR_REQUEST_ID)
                reportMirrorUnavailable("Screen sharing was not approved")
            }
            ACTION_MIRROR_STOP -> mirror.stop("Stopped from the PC")
            ACTION_SYNC_SMS -> {
                if (!prefs.isPaired) {
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                if (ConnectionHub.isConnected()) sendSmsChanged() else startLoop()
            }
            ACTION_REFRESH_INFO -> {
                if (!prefs.isPaired) {
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                if (ConnectionHub.isConnected()) {
                    sendPhoneInfo()
                    sendBattery(force = true)
                    mainHandler.post { calls.start() }
                } else {
                    startLoop()
                }
            }
            else -> {
                if (!prefs.isPaired || !prefs.desiredConnected) {
                    LinkStatus.setState(LinkState.Stopped)
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                startLoop()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        LinkStatus.log("Service", "Task removed; keeping connection")
        if (!prefs.desiredConnected) {
            stopSelfResult(lastStartId)
            return
        }
        refreshNotification()
    }

    override fun onDestroy() {
        destroyed = true
        shutdownConnection()
        releaseReconnectWakeLock()
        runCatching { calls.stop() }
        networkCallback?.let { callback ->
            runCatching {
                getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback)
            }
        }
        networkCallback = null
        unregisterBatteryReceiver()
        releaseLocks()
        mainHandler.removeCallbacksAndMessages(null)
        scope.cancel()
        if (!explicitStop && prefs.desiredConnected) {
            if (LinkStatus.state.value !is LinkState.Error) {
                LinkStatus.setState(LinkState.Connecting("Reconnecting…"))
            }
        } else if (LinkStatus.state.value !is LinkState.Error) {
            LinkStatus.setState(LinkState.Stopped)
        }
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        LinkStatus.log("Service", "Destroyed")
        super.onDestroy()
    }

    @Synchronized
    private fun startLoop() {
        if (loopJob?.isActive == true) return
        LinkStatus.setState(LinkState.Connecting("Connecting…"))
        refreshNotification()
        loopJob = scope.launch { connectionLoop() }
    }

    private fun shutdownConnection() {
        loopJob?.cancel()
        loopJob = null
        currentConnector?.shutdown()
        currentConnector = null
        ConnectionHub.detach(currentSession)
        currentSession = null
        stopFeatureObservers()
        releaseReconnectWakeLock()
        runCatching { mirror.stop("Disconnected") }
    }

    private suspend fun connectionLoop() {
        val backoff = Backoff()
        var lastConnectFailed = false

        while (currentCoroutineContext().isActive) {
            acquireReconnectWakeLock()
            val paired = prefs.pairedPc ?: break
            LinkStatus.setState(LinkState.Connecting("Connecting to ${paired.pcName}…"))
            refreshNotification()

            if (lastConnectFailed) {
                val discovered = runCatching {
                    PcDiscovery(this@LinkConnectionService).find(paired.pcId)
                }.getOrNull()
                if (discovered != null) {
                    val advertised = normalizeFingerprint(discovered.fingerprint)
                    if (advertised == null || advertised == paired.fingerprint) {
                        prefs.updateTarget(discovered.host, discovered.port)
                        LinkStatus.log(
                            "Net",
                            "mDNS found ${discovered.host}:${discovered.port}",
                        )
                    }
                }
            }

            val target = prefs.pairedPc ?: break
            val closed = CompletableDeferred<String>()
            val connector = PhoneConnector(
                identity = identity,
                phoneId = prefs.phoneId,
                phoneName = prefs.phoneName,
                appVersion = BuildConfig.VERSION_NAME,
                onMessage = ::handleEnvelope,
                onBinary = {},
                onClosed = { reason ->
                    if (!closed.isCompleted) closed.complete(reason)
                },
            )
            currentConnector = connector
            var retry = true

            try {
                val session = connector.connect(target, target.host, target.port)
                currentSession = session
                ConnectionHub.attach(session)
                lastConnectFailed = false
                backoff.reset()
                // A wake-up queued while connecting would otherwise skip the
                // next backoff and spin the loop.
                while (reconnectSignal.tryReceive().isSuccess) {
                    // Drain stale signals.
                }
                LinkStatus.setState(LinkState.Connected(target.pcName, target.host))
                releaseReconnectWakeLock()
                LinkStatus.log("Net", "Authenticated with ${target.pcName} at ${target.host}")
                refreshNotification()
                startFeatureObservers()
                sendPhoneInfo()
                sendBattery(force = true)
                sendSmsChanged()
                val reason = closed.await()
                LinkStatus.log("Net", "Connection ended: $reason")
            } catch (error: LinkAuthException) {
                if (error.code == ErrorCodes.BUSY) {
                    LinkStatus.log("Net", "PC is busy; will retry")
                    LinkStatus.setState(
                        LinkState.Connecting("PC is busy; retrying…"),
                        error.message,
                    )
                } else {
                    retry = false
                    LinkStatus.setState(
                        LinkState.Error(error.message ?: "Authentication failed"),
                        error.message,
                    )
                    LinkStatus.log("Net", "Authentication failed: ${error.message}")
                    postAlert(
                        "LinkPhone needs attention",
                        error.message ?: "Authentication failed. Pair the phone again.",
                    )
                }
            } catch (error: LinkProtocolException) {
                retry = false
                LinkStatus.setState(
                    LinkState.Error(error.message ?: "Protocol error"),
                    error.message,
                )
                LinkStatus.log("Net", "Protocol error: ${error.message}")
                postAlert(
                    "LinkPhone protocol error",
                    error.message ?: "The phone and PC may be running different versions.",
                )
            } catch (error: SSLHandshakeException) {
                retry = false
                LinkStatus.setState(
                    LinkState.Error("PC certificate changed. Pair this phone again."),
                    error.message,
                )
                LinkStatus.log("Net", "TLS pin mismatch")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                lastConnectFailed = true
                LinkStatus.setState(LinkState.Connecting("Reconnecting…"), error.message)
                LinkStatus.log("Net", "Connection failed: ${error.message}")
            } finally {
                ConnectionHub.detach(currentSession)
                currentSession = null
                currentConnector = null
                stopFeatureObservers()
                connector.shutdown()
            }

            if (!retry) break

            acquireReconnectWakeLock()
            val delayMs = backoff.nextDelayMs()
            LinkStatus.setState(LinkState.Connecting("Reconnecting in ${delayMs / 1000}s…"))
            refreshNotification()
            withTimeoutOrNull(delayMs) { reconnectSignal.receive() }
        }

        releaseReconnectWakeLock()
        if (!destroyed) {
            if (LinkStatus.state.value !is LinkState.Error) {
                LinkStatus.setState(LinkState.Stopped)
            }
            refreshNotification()
            stopSelfResult(lastStartId)
        }
    }

    private fun startFeatureObservers() {
        if (Permissions.sms(this)) {
            smsObserver?.stop()
            smsObserver = SmsObserver(this) { scheduleSmsChanged() }.also { it.start() }
        }
        mainHandler.post { calls.start() }
    }

    private fun stopFeatureObservers() {
        smsObserver?.stop()
        smsObserver = null
        smsDebounceJob?.cancel()
        smsDebounceJob = null
        mainHandler.post { calls.stop() }
    }

    private fun scheduleSmsChanged() {
        smsDebounceJob?.cancel()
        smsDebounceJob = scope.launch {
            delay(700)
            sendSmsChanged()
        }
    }

    private fun sendSmsChanged() {
        if (!ConnectionHub.isConnected()) return
        ConnectionHub.sendEvent(
            Types.SMS_CHANGED,
            ProtocolCodec.data(SmsChanged(System.currentTimeMillis())),
        )
    }

    private fun sendPhoneInfo() {
        if (!ConnectionHub.isConnected()) return
        ConnectionHub.sendEvent(
            Types.PHONE_INFO,
            ProtocolCodec.data(PhoneInfoBuilder.build(this, BuildConfig.VERSION_NAME)),
        )
    }

    /**
     * Sends the phone's charge level. The battery broadcast fires more often
     * than the charge actually changes, so unchanged readings are skipped.
     */
    private fun sendBattery(force: Boolean = false) {
        val status = BatteryReader.read(this) ?: return
        publishBattery(status, force)
    }

    private fun publishBattery(status: BatteryStatus, force: Boolean = false) {
        if (!ConnectionHub.isConnected()) return
        if (!force && status.level == lastBatteryLevel && status.charging == lastBatteryCharging) {
            return
        }
        lastBatteryLevel = status.level
        lastBatteryCharging = status.charging
        ConnectionHub.sendEvent(
            Types.PHONE_BATTERY,
            ProtocolCodec.data(BatteryStatusWire(status.level, status.charging)),
        )
    }

    private fun registerBatteryReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val status = BatteryReader.parse(intent) ?: return
                publishBattery(status)
            }
        }
        val registered = runCatching {
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        if (registered.isFailure) {
            LinkStatus.log("Service", "Could not watch the battery level")
            return
        }
        batteryReceiver = receiver
    }

    private fun unregisterBatteryReceiver() {
        batteryReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
        }
        batteryReceiver = null
    }

    private fun sendCallState(state: CallState) {
        if (!ConnectionHub.isConnected()) return
        ConnectionHub.sendEvent(Types.CALL_STATE, ProtocolCodec.data(state))
    }

    private fun sendMirrorState(state: MirrorState) {
        ConnectionHub.sendEvent(Types.MIRROR_STATE, ProtocolCodec.data(state))
        if (!state.active) refreshNotification()
    }

    @Volatile
    private var mirrorSendFailed = false

    private fun sendMirrorFrame(frame: ByteArray) {
        if (mirrorSendFailed) return
        if (ConnectionHub.sendBinary(frame)) return
        mirrorSendFailed = true
        // The socket is gone; stop encoding instead of burning the battery.
        mainHandler.post { runCatching { mirror.stop("The connection closed") } }
    }

    private fun onMirrorStopped() {
        refreshNotification()
    }

    private fun handleEnvelope(envelope: Envelope) {
        if (envelope.replyTo != null) return
        scope.launch {
            try {
                commandGate.withPermit { route(envelope) }
            } catch (error: FeaturePermissionException) {
                replyError(envelope, ErrorCodes.PERMISSION_DENIED, error.message ?: "Permission missing")
            } catch (error: IllegalArgumentException) {
                replyError(envelope, ErrorCodes.BAD_REQUEST, error.message ?: "Invalid request")
            } catch (cancelled: CancellationException) {
                // Waiting for a command slot can be cancelled when the service
                // stops; do not turn that into an INTERNAL error reply.
                throw cancelled
            } catch (error: Exception) {
                LinkStatus.log(
                    "Net",
                    "Command ${envelope.type} failed: ${error.javaClass.simpleName}",
                )
                replyError(
                    envelope,
                    ErrorCodes.INTERNAL,
                    error.message ?: "Command failed",
                )
            }
        }
    }

    private fun route(envelope: Envelope) {
        when (envelope.type) {
            Types.PING -> reply(envelope, ProtocolCodec.data(Pong(System.currentTimeMillis())))

            Types.SMS_LIST -> {
                val request = envelope.decodeData<SmsListRequest>()
                val messages = smsReader.listMessages(request.limit)
                // The PC closes the session above 1 MiB, so trim here rather
                // than start a reconnect loop that re-requests the same list.
                val fitted = SmsPayload.fit(
                    messages = messages,
                    sizeOf = { candidate ->
                        ProtocolCodec.data(SmsListResult(candidate))
                            .toString()
                            .toByteArray(Charsets.UTF_8)
                            .size
                    },
                )
                reply(envelope, ProtocolCodec.data(SmsListResult(fitted)))
            }

            Types.SMS_SEND -> {
                val request = envelope.decodeData<SmsSendRequest>()
                val result = smsSender.send(request.address, request.body, request.subId)
                reply(envelope, ProtocolCodec.data(result))
            }

            Types.SMS_OPEN_THREAD -> {
                val request = envelope.decodeData<SmsOpenThreadRequest>()
                val address = SafeInput.normalizeSmsAddress(request.address)
                if (address == null) {
                    replyError(envelope, ErrorCodes.BAD_REQUEST, "Invalid conversation address")
                    return
                }
                if (!Permissions.notifications(this)) {
                    replyError(
                        envelope,
                        ErrorCodes.PERMISSION_DENIED,
                        "Allow notifications on the phone to show the open-conversation prompt",
                    )
                    return
                }
                postOpenSmsNotification(address)
                replyOk(envelope)
            }

            Types.NOTIF_DISMISS -> {
                val request = envelope.decodeData<NotificationDismissRequest>()
                if (LinkNotificationListener.dismiss(request.key)) {
                    replyOk(envelope)
                } else {
                    replyError(
                        envelope,
                        ErrorCodes.PERMISSION_DENIED,
                        "Notification access is not available on the phone",
                    )
                }
            }

            Types.NOTIF_LIST -> {
                val notifications = LinkNotificationListener.active()
                if (notifications == null) {
                    replyError(
                        envelope,
                        ErrorCodes.PERMISSION_DENIED,
                        "Notification access is not available on the phone",
                    )
                } else {
                    reply(envelope, ProtocolCodec.data(NotificationListResult(notifications)))
                }
            }

            Types.PHOTO_LIST -> {
                val request = envelope.decodeData<PhotoListRequest>()
                val page = photos.list(
                    limit = request.limit,
                    bucket = request.bucket,
                    beforeDate = request.beforeDate,
                    beforeId = request.beforeId,
                )
                reply(
                    envelope,
                    ProtocolCodec.data(PhotoListResult(page.photos, page.hasMore)),
                )
            }

            Types.PHOTO_ALBUMS -> {
                reply(envelope, ProtocolCodec.data(PhotoAlbumListResult(photos.albums())))
            }

            Types.PHOTO_FETCH -> streamPhoto(envelope)

            Types.CALL_DIAL -> {
                val request = envelope.decodeData<CallDialRequest>()
                resultReply(envelope, calls.placeCall(request.number, request.subId))
            }

            Types.CALL_ANSWER -> resultReply(envelope, calls.answer())
            Types.CALL_DECLINE -> resultReply(envelope, calls.decline())
            Types.CALL_SPEAKER -> {
                val request = envelope.decodeData<CallSpeakerRequest>()
                resultReply(envelope, calls.setSpeaker(request.enabled))
            }

            Types.CONTACTS_LIST -> {
                val request = envelope.decodeData<ContactListRequest>()
                val contacts = contactRepository.list(request.limit)
                reply(envelope, ProtocolCodec.data(ContactListResult(contacts)))
            }

            Types.CLIPBOARD_SET -> {
                val request = envelope.decodeData<ClipboardSetRequest>()
                val text = SafeInput.truncateClipboard(request.text)
                if (text.isBlank()) {
                    replyError(envelope, ErrorCodes.BAD_REQUEST, "Clipboard text is empty")
                    return
                }
                ClipboardBridge.write(this, text)
                postClipboardNotification(text)
                replyOk(envelope)
            }

            Types.HANDOFF_OPEN -> {
                val request = envelope.decodeData<HandoffOpenRequest>()
                if (!SafeInput.isSafeHttpUrl(request.url)) {
                    replyError(envelope, ErrorCodes.BAD_REQUEST, "Only http(s) links can be opened")
                    return
                }
                postOpenLinkNotification(request.url)
                replyOk(envelope)
            }

            Types.MIRROR_START -> {
                if (mirror.isActive()) {
                    replyOk(envelope)
                    return
                }
                if (!Permissions.notifications(this)) {
                    replyError(
                        envelope,
                        ErrorCodes.PERMISSION_DENIED,
                        "Allow notifications on the phone so the screen-sharing prompt can be shown",
                    )
                    return
                }
                val request = runCatching { envelope.decodeData<MirrorStartRequest>() }
                    .getOrDefault(MirrorStartRequest())
                postMirrorConsentNotification(
                    codec = MirrorCodecChoice.normalize(request.codec),
                    fps = MirrorCodecChoice.normalizeFps(request.fps),
                )
                replyOk(envelope)
            }

            Types.MIRROR_STOP -> {
                mirror.stop("Stopped from the PC")
                replyOk(envelope)
            }

            Types.MIRROR_KEYFRAME -> {
                mirror.requestKeyframe()
                replyOk(envelope)
            }

            Types.MIRROR_TOUCH -> {
                val request = envelope.decodeData<MirrorTouchRequest>()
                if (request.action != "tap" && request.action != "swipe") {
                    replyError(envelope, ErrorCodes.BAD_REQUEST, "Unknown touch action")
                    return
                }
                val performed = LinkAccessibilityService.performTouch(
                    action = request.action,
                    x1 = request.x,
                    y1 = request.y,
                    x2 = request.x2,
                    y2 = request.y2,
                    durationMs = request.durationMs,
                )
                if (performed) {
                    replyOk(envelope)
                } else {
                    replyError(
                        envelope,
                        ErrorCodes.PERMISSION_DENIED,
                        "Enable the LinkPhone Accessibility service on the phone to send taps",
                    )
                }
            }

            else -> replyError(
                envelope,
                ErrorCodes.UNSUPPORTED,
                "Unsupported command: ${envelope.type}",
            )
        }
    }

    private fun streamPhoto(envelope: Envelope) {
        val request = envelope.decodeData<PhotoFetchRequest>()
        if (request.kind != "thumb" && request.kind != "full") {
            replyError(envelope, ErrorCodes.BAD_REQUEST, "Unknown photo kind")
            return
        }
        photoGate.acquireUninterruptibly()
        val bytes = try {
            photos.loadJpeg(request.id, request.kind, request.volume)
        } finally {
            photoGate.release()
        }
        if (bytes == null) {
            replyError(envelope, ErrorCodes.NOT_FOUND, "Photo not found or could not be decoded")
            return
        }
        if (bytes.size > MAX_TRANSFER_BYTES) {
            replyError(envelope, ErrorCodes.TOO_LARGE, "Photo is too large to transfer")
            return
        }

        val streamId = streamCounter.getAndIncrement()
        reply(
            envelope,
            ProtocolCodec.data(
                PhotoStreamMeta(
                    streamId = streamId,
                    photoId = request.id,
                    kind = request.kind,
                    mime = "image/jpeg",
                    size = bytes.size.toLong(),
                ),
            ),
        )

        var offset = 0
        var sequence = 0
        while (offset < bytes.size) {
            if (!ConnectionHub.isConnected()) return
            val end = min(offset + BinaryCodec.CHUNK_SIZE, bytes.size)
            val last = end == bytes.size
            val frame = BinaryCodec.encode(
                type = BinaryCodec.TYPE_PHOTO,
                streamId = streamId,
                seq = sequence++,
                last = last,
                payload = bytes.copyOfRange(offset, end),
            )
            if (!ConnectionHub.sendBinary(frame)) return
            offset = end
        }
        ConnectionHub.sendEvent(
            Types.PHOTO_STREAM_END,
            ProtocolCodec.data(StreamEnd(streamId)),
        )
    }

    private fun resultReply(envelope: Envelope, result: Result<Unit>) {
        result.fold(
            onSuccess = { replyOk(envelope) },
            onFailure = { error ->
                val code = if (error is FeaturePermissionException) {
                    ErrorCodes.PERMISSION_DENIED
                } else {
                    ErrorCodes.BAD_REQUEST
                }
                replyError(envelope, code, error.message ?: "Call action failed")
            },
        )
    }

    private fun reply(request: Envelope, data: JsonElement? = null) {
        ConnectionHub.sendEnvelope(ProtocolCodec.response(request, data))
    }

    private fun replyOk(request: Envelope) {
        reply(request, ProtocolCodec.data(OkResult()))
    }

    private fun replyError(request: Envelope, code: String, message: String) {
        ConnectionHub.sendEnvelope(ProtocolCodec.failure(request, code, message))
    }

    @Suppress("DEPRECATION")
    private fun handleMirrorStart(intent: Intent) {
        val resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            cancelAlert(MIRROR_REQUEST_ID)
            reportMirrorUnavailable("Screen sharing was not approved")
            postAlert("Screen sharing not started", "Screen capture consent was cancelled.")
            return
        }
        if (!startForegroundCompat(includeMediaProjection = true)) {
            reportMirrorUnavailable("Screen sharing could not start")
            return
        }
        try {
            mirrorSendFailed = false
            mirror.start(
                resultCode = resultCode,
                data = resultData,
                requestedCodec = MirrorCodecChoice.normalize(
                    intent.getStringExtra(EXTRA_MIRROR_CODEC),
                ),
                requestedFps = MirrorCodecChoice.normalizeFps(
                    intent.getIntExtra(EXTRA_MIRROR_FPS, MirrorCodecChoice.FPS_30),
                ),
            )
            cancelAlert(MIRROR_REQUEST_ID)
        } catch (error: Exception) {
            LinkStatus.log("Mirror", "Start failed: ${error.message}")
            reportMirrorUnavailable(error.message ?: "Could not start screen capture.")
            startForegroundCompat(includeMediaProjection = false)
            postAlert(
                "Screen sharing failed",
                error.message ?: "Could not start screen capture.",
            )
        }
    }

    private fun reportMirrorUnavailable(message: String) {
        if (mirror.isActive()) return
        ConnectionHub.sendEvent(
            Types.MIRROR_STATE,
            ProtocolCodec.data(MirrorState(active = false, message = message)),
        )
    }

    private fun startForegroundCompat(includeMediaProjection: Boolean = false): Boolean {
        val baseType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        val requested = baseType or
            (if (includeMediaProjection) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                0
            })
        if (tryStartForeground(requested)) return true
        if (includeMediaProjection && tryStartForeground(baseType)) {
            LinkStatus.log("Service", "Foreground started without media projection")
            return true
        }
        LinkStatus.setState(
            LinkState.Error("LinkPhone could not start its foreground service."),
        )
        LinkStatus.log("Service", "Foreground service unavailable; stopping safely")
        stopSelfResult(lastStartId)
        return false
    }

    private fun tryStartForeground(types: Int): Boolean = try {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), types)
        true
    } catch (error: Exception) {
        LinkStatus.log(
            "Service",
            "Foreground start failed (types=$types): ${error.javaClass.simpleName}",
        )
        false
    }

    private fun refreshNotification() {
        mainHandler.post {
            if (!destroyed) startForegroundCompat(mirror.isActive())
        }
    }

    private fun buildNotification(): Notification {
        val state = LinkStatus.state.value
        val title: String
        val text: String
        when (state) {
            is LinkState.Connected -> {
                title = "Connected to ${state.pcName}"
                text = "LinkPhone is running • ${state.host}"
            }
            is LinkState.Connecting -> {
                title = "LinkPhone"
                text = state.detail
            }
            is LinkState.Error -> {
                title = "LinkPhone disconnected"
                text = state.message
            }
            LinkState.Stopped -> {
                title = "LinkPhone"
                text = "Disconnected"
            }
        }

        val openApp = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnect = PendingIntent.getService(
            this,
            2,
            Intent(this, LinkConnectionService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val clipboard = PendingIntent.getActivity(
            this,
            3,
            Intent(this, SendClipboardActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_CONNECTION)
            .setSmallIcon(R.drawable.ic_stat_link)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                NotificationCompat.Action(
                    0,
                    "Send clipboard",
                    clipboard,
                ),
            )

        if (state is LinkState.Connected) {
            builder.addAction(NotificationCompat.Action(0, "Disconnect", disconnect))
        }
        if (mirror.isActive()) {
            builder.setSubText("Screen mirroring active")
        }
        return builder.build()
    }

    private fun postClipboardNotification(text: String) {
        val intent = Intent(this, ClipboardApplyActivity::class.java)
            .putExtra(EXTRA_CLIPBOARD_TEXT, text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            this,
            201,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_link)
            .setContentTitle("Copied from PC")
            .setContentText("Tap to copy the text on this phone")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
        notifySafely(CLIPBOARD_NOTIFICATION_ID, notification)
    }

    private fun postOpenSmsNotification(address: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.fromParts("sms", address, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            this,
            204,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_link)
            .setContentTitle("Open conversation on phone")
            .setContentText("Tap to open the conversation with $address in your SMS app")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Tap to open the conversation with $address in your default SMS app. " +
                        "That app marks it read; LinkPhone does not modify your messages.",
                ),
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        notifySafely(SMS_OPEN_NOTIFICATION_ID, builder.build())
    }

    private fun postOpenLinkNotification(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            this,
            202,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val host = runCatching { Uri.parse(url).host }.getOrNull() ?: "the link"
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_link)
            .setContentTitle("Link from PC")
            .setContentText("Tap to open $host on this phone")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notifySafely(LINK_NOTIFICATION_ID, notification)
    }

    private fun postMirrorConsentNotification(codec: String, fps: Int) {
        val intent = Intent(this, MirrorConsentActivity::class.java)
            // Carry the request with the consent so a second request cannot
            // overwrite the codec this one will start with.
            .putExtra(EXTRA_MIRROR_CODEC, codec)
            .putExtra(EXTRA_MIRROR_FPS, fps)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            this,
            203,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cancelIntent = PendingIntent.getService(
            this,
            204,
            Intent(this, LinkConnectionService::class.java)
                .setAction(ACTION_MIRROR_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_MIRROR)
            .setSmallIcon(R.drawable.ic_stat_link)
            .setContentTitle("Screen mirroring requested")
            .setContentText("Tap to allow your PC to view this screen. Consent is required each session.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Your PC asked to view this screen. Android requires you to approve " +
                        "screen capture for every session. Tap to choose what to share.",
                ),
            )
            .setContentIntent(pending)
            .setDeleteIntent(cancelIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
        notifySafely(MIRROR_REQUEST_ID, builder.build())
    }

    private fun postAlert(title: String, text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_link)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notifySafely(ALERT_NOTIFICATION_ID, notification)
    }

    @SuppressLint("MissingPermission")
    private fun notifySafely(id: Int, notification: Notification) {
        runCatching { NotificationManagerCompat.from(this).notify(id, notification) }
    }

    private fun cancelAlert(id: Int) {
        runCatching { NotificationManagerCompat.from(this).cancel(id) }
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CONNECTION,
                "Connection",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Ongoing phone-to-PC connection"
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                "Alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Clipboard, link handoff and mirroring alerts"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MIRROR,
                "Screen mirroring",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Screen-sharing consent requests"
            },
        )
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        val wifi = applicationContext.getSystemService(WifiManager::class.java) ?: return
        runCatching {
            wifiLock = wifi.createWifiLock(
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                "LinkPhone:wifi",
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
            multicastLock = wifi.createMulticastLock("LinkPhone:mdns").apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onFailure {
            LinkStatus.log("Service", "Wi-Fi locks unavailable")
        }
    }

    @Synchronized
    private fun acquireReconnectWakeLock() {
        if (reconnectWakeLock?.isHeld == true) return
        runCatching {
            val power = getSystemService(PowerManager::class.java) ?: return
            reconnectWakeLock = power.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "LinkPhone:reconnect",
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
            LinkStatus.log("Service", "Holding a wake lock while reconnecting")
        }.onFailure {
            LinkStatus.log("Service", "Reconnect wake lock unavailable")
        }
    }

    @Synchronized
    private fun releaseReconnectWakeLock() {
        runCatching { if (reconnectWakeLock?.isHeld == true) reconnectWakeLock?.release() }
        reconnectWakeLock = null
    }

    private fun releaseLocks() {
        releaseReconnectWakeLock()
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
        runCatching { if (multicastLock?.isHeld == true) multicastLock?.release() }
        wifiLock = null
        multicastLock = null
    }

    private fun registerNetworkCallback() {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                reconnectSignal.trySend(Unit)
            }
        }
        runCatching {
            manager.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        }
    }

    companion object {
        const val ACTION_CONNECT = "app.linkphone.action.CONNECT"
        const val ACTION_DISCONNECT = "app.linkphone.action.DISCONNECT"
        const val ACTION_FORGET = "app.linkphone.action.FORGET"
        const val ACTION_SYNC_SMS = "app.linkphone.action.SYNC_SMS"
        const val ACTION_REFRESH_INFO = "app.linkphone.action.REFRESH_INFO"
        const val ACTION_MIRROR_START = "app.linkphone.action.MIRROR_START"
        const val ACTION_MIRROR_CANCEL = "app.linkphone.action.MIRROR_CANCEL"
        const val ACTION_MIRROR_STOP = "app.linkphone.action.MIRROR_STOP"

        const val EXTRA_RESULT_CODE = "app.linkphone.extra.RESULT_CODE"
        const val EXTRA_RESULT_DATA = "app.linkphone.extra.RESULT_DATA"
        const val EXTRA_CLIPBOARD_TEXT = "app.linkphone.extra.CLIPBOARD_TEXT"
        const val EXTRA_MIRROR_CODEC = "app.linkphone.extra.MIRROR_CODEC"
        const val EXTRA_MIRROR_FPS = "app.linkphone.extra.MIRROR_FPS"

        const val CHANNEL_CONNECTION = "connection"
        const val CHANNEL_ALERTS = "alerts"
        const val CHANNEL_MIRROR = "mirror"

        const val NOTIFICATION_ID = 1001
        const val MIRROR_REQUEST_ID = 1002
        const val CLIPBOARD_NOTIFICATION_ID = 1003
        const val LINK_NOTIFICATION_ID = 1004
        const val ALERT_NOTIFICATION_ID = 1005
        const val SMS_OPEN_NOTIFICATION_ID = 1006

        private const val MAX_TRANSFER_BYTES = 8 * 1024 * 1024

        fun start(context: Context) {
            runCatching {
                LinkPrefs(context).desiredConnected = true
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, LinkConnectionService::class.java)
                        .setAction(ACTION_CONNECT),
                )
            }
        }

        fun disconnect(context: Context) {
            runCatching {
                LinkPrefs(context).desiredConnected = false
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, LinkConnectionService::class.java)
                        .setAction(ACTION_DISCONNECT),
                )
            }
        }

        fun forget(context: Context) {
            runCatching {
                LinkPrefs(context).desiredConnected = false
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, LinkConnectionService::class.java)
                        .setAction(ACTION_FORGET),
                )
            }
        }

        fun refreshInfo(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, LinkConnectionService::class.java)
                        .setAction(ACTION_REFRESH_INFO),
                )
            }
        }

        fun stopMirror(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, LinkConnectionService::class.java)
                        .setAction(ACTION_MIRROR_STOP),
                )
            }
        }
    }
}
