package app.linkphone.core.update

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.linkphone.BuildConfig
import app.linkphone.MainActivity
import app.linkphone.R
import app.linkphone.core.prefs.LinkPrefs
import app.linkphone.core.util.LinkStatus
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class UpdatePhase {
    Unsupported,
    Idle,
    Checking,
    UpToDate,
    Available,
    Downloading,
    Ready,
    Error,
}

enum class UpdateMode { Installed, Disabled }

data class UpdateStatus(
    val supported: Boolean,
    val mode: UpdateMode,
    val phase: UpdatePhase,
    val currentVersion: String,
    val version: String? = null,
    val notes: String? = null,
    val progress: Int? = null,
    val checkedAt: Long? = null,
    val error: String? = null,
)

data class UpdateEngineOptions(
    val mode: UpdateMode,
    val currentVersion: String,
    val autoCheck: Boolean,
    val client: UpdateClient?,
    val updatesDir: File,
    val scope: CoroutineScope,
    val onStatus: (UpdateStatus) -> Unit = {},
    val notify: (title: String, body: String) -> Unit = { _, _ -> },
    val verifySignature: (File) -> String? = { null },
    val lastCheckAt: () -> Long = { 0L },
    val recordCheckAt: (Long) -> Unit = {},
    val now: () -> Long = { System.currentTimeMillis() },
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
)

/**
 * The update state machine. Everything Android-specific arrives through
 * [UpdateEngineOptions], so the whole flow stays testable on the JVM.
 */
class UpdateEngine(private val options: UpdateEngineOptions) {

    private val _status = MutableStateFlow(initialStatus())
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    /** The verified APK, or null while nothing is ready to install. */
    @Volatile
    var readyApk: File? = null
        private set

    private var release: ReleaseInfo? = null
    private var timerJob: Job? = null
    private var autoCheck = options.autoCheck
    private var lastProgress = -1
    private var lastProgressAt = 0L
    private var notifiedVersion: String? = null

    /** Serializes checks and downloads so two of them never share a file. */
    private val mutex = Mutex()

    fun start() {
        cleanupStaleFiles()
        if (!canRun()) return
        if (autoCheck) scheduleAutoCheck()
    }

    fun stop() {
        timerJob?.cancel()
        timerJob = null
    }

    fun setAutoCheck(enabled: Boolean) {
        autoCheck = enabled
        if (!canRun()) {
            stop()
            return
        }
        if (enabled) scheduleAutoCheck() else stop()
    }

    /** Checks for a newer release. A manual check ignores the throttle. */
    suspend fun check(manual: Boolean = true) {
        if (!canRun()) return
        if (!manual && isThrottled()) return
        mutex.withLock { performCheck() }
    }

    /** Downloads the release that was found by the last check. */
    suspend fun download() {
        mutex.withLock { performDownload() }
    }

    private suspend fun performDownload() {
        val client = options.client ?: return
        val release = this.release ?: return
        val target = File(options.updatesDir, apkFileName(release.version))
        val partial = File(target.absolutePath + ".part")

        update { it.copy(phase = UpdatePhase.Downloading, progress = 0, error = null) }
        try {
            val expected = parseChecksum(
                client.readText(release.checksums.url),
                target.name,
            ) ?: throw UpdateException("SHA256SUMS.txt does not list ${target.name}")

            if (target.exists() && matchesChecksum(target, expected)) {
                val failure = withContext(options.ioDispatcher) {
                    options.verifySignature(target)
                }
                if (failure != null) {
                    withContext(options.ioDispatcher) { target.delete() }
                    throw UpdateException(failure)
                }
                readyApk = target
                update { it.copy(phase = UpdatePhase.Ready, progress = 100) }
                announce(release.version)
                return
            }

            client.download(release.apk.url, partial, MAX_APK_BYTES) { percent ->
                reportProgress(percent)
            }
            val failure = withContext(options.ioDispatcher) {
                val digest = sha256File(partial)
                if (!checksumsEqual(digest, expected)) {
                    "The downloaded update failed its SHA-256 check"
                } else {
                    options.verifySignature(partial)
                }
            }
            if (failure != null) throw UpdateException(failure)
            val saveError = withContext(options.ioDispatcher) {
                if (target.exists() && !target.delete()) {
                    "Could not replace the previous update."
                } else if (!partial.renameTo(target)) {
                    "Could not save the downloaded update."
                } else {
                    null
                }
            }
            if (saveError != null) throw UpdateException(saveError)
            readyApk = target
            update { it.copy(phase = UpdatePhase.Ready, progress = 100) }
            announce(release.version)
        } catch (cancellation: CancellationException) {
            partial.delete()
            update { it.copy(phase = UpdatePhase.Idle, progress = null) }
            throw cancellation
        } catch (error: Exception) {
            partial.delete()
            readyApk = null
            notifiedVersion = null
            fail(error.message ?: "The update download failed.")
        }
    }

    private suspend fun performCheck() {
        val client = options.client ?: return
        val previous = _status.value
        update { it.copy(phase = UpdatePhase.Checking, error = null) }
        try {
            val found = client.latestRelease()
            val checkedAt = options.now()
            options.recordCheckAt(checkedAt)
            if (found == null || !isNewer(found.version)) {
                release = null
                // A downloaded update that is no longer newer than this build is obsolete.
                if (found != null) readyApk = null
                update {
                    it.copy(
                        phase = UpdatePhase.UpToDate,
                        version = null,
                        notes = null,
                        progress = null,
                        checkedAt = checkedAt,
                    )
                }
                return
            }
            if (previous.phase == UpdatePhase.Ready &&
                previous.version == found.version &&
                readyApk?.exists() == true
            ) {
                release = found
                update {
                    it.copy(
                        phase = UpdatePhase.Ready,
                        version = found.version,
                        notes = found.notes,
                        progress = 100,
                        checkedAt = checkedAt,
                    )
                }
                return
            }
            release = found
            update {
                it.copy(
                    phase = UpdatePhase.Available,
                    version = found.version,
                    notes = found.notes,
                    progress = null,
                    checkedAt = checkedAt,
                )
            }
            performDownload()
        } catch (cancellation: CancellationException) {
            update { it.copy(phase = UpdatePhase.Idle) }
            throw cancellation
        } catch (error: Exception) {
            fail(error.message ?: "Could not check for updates.")
        }
    }

    private fun isNewer(version: String): Boolean = runCatching {
        isNewerVersion(version, options.currentVersion)
    }.getOrDefault(false)

    private fun canRun(): Boolean =
        options.mode == UpdateMode.Installed && options.client != null

    private fun isThrottled(): Boolean {
        val last = options.lastCheckAt()
        if (last <= 0L) return false
        return options.now() - last < AUTO_CHECK_INTERVAL_MS
    }

    private fun scheduleAutoCheck() {
        timerJob?.cancel()
        timerJob = options.scope.launch {
            delay(INITIAL_CHECK_DELAY_MS)
            check(manual = false)
            while (true) {
                delay(AUTO_CHECK_INTERVAL_MS)
                check(manual = false)
            }
        }
    }

    private fun reportProgress(percent: Int) {
        val value = percent.coerceIn(0, 100)
        val now = options.now()
        if (value < 100 && now - lastProgressAt < PROGRESS_INTERVAL_MS) return
        if (value == lastProgress) return
        lastProgress = value
        lastProgressAt = now
        update { it.copy(progress = value) }
    }

    private suspend fun matchesChecksum(file: File, expected: String): Boolean =
        withContext(options.ioDispatcher) { checksumsEqual(sha256File(file), expected) }

    private fun announce(version: String) {
        if (notifiedVersion == version) return
        notifiedVersion = version
        options.notify(
            "Update ready",
            "LinkPhone $version is downloaded. Open LinkPhone to install it.",
        )
    }

    private fun fail(message: String) {
        update { it.copy(phase = UpdatePhase.Error, error = message.take(300), progress = null) }
    }

    private fun update(transform: (UpdateStatus) -> UpdateStatus) {
        val next = transform(_status.value)
        _status.value = next
        options.onStatus(next)
    }

    /** Removes partial downloads and every APK except the newest one. */
    private fun cleanupStaleFiles() {
        val files = options.updatesDir.listFiles() ?: return
        val apks = files
            .filter { it.name.startsWith(APK_PREFIX) && it.name.endsWith(APK_SUFFIX) }
        val keep = apks
            .mapNotNull { apk -> apkVersion(apk.name)?.let { apk to it } }
            .maxWithOrNull { (_, left), (_, right) -> compareVersions(left, right) }
            ?.first
        for (file in files) {
            if (file.name.endsWith(PART_SUFFIX)) {
                file.delete()
                continue
            }
            if (file in apks && file != keep) file.delete()
        }
    }

    private fun apkVersion(name: String): String? {
        val trimmed = name.removePrefix(APK_PREFIX).removeSuffix(APK_SUFFIX)
        return normalizeVersion(trimmed)
    }

    private fun initialStatus(): UpdateStatus = UpdateStatus(
        supported = canRun(),
        mode = options.mode,
        phase = if (canRun()) UpdatePhase.Idle else UpdatePhase.Unsupported,
        currentVersion = options.currentVersion,
    )

    companion object {
        const val INITIAL_CHECK_DELAY_MS = 15_000L
        const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1_000L
        const val PROGRESS_INTERVAL_MS = 200L
        const val MAX_APK_BYTES = 300 * 1024 * 1024
        const val APK_PREFIX = "LinkPhone-"
        const val APK_SUFFIX = ".apk"
        const val PART_SUFFIX = ".part"
    }
}

/**
 * Android wiring for the updater: preferences, notifications, the installer
 * and an app-scoped coroutine.
 */
object AppUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow(
        UpdateStatus(
            supported = false,
            mode = UpdateMode.Disabled,
            phase = UpdatePhase.Unsupported,
            currentVersion = "",
        ),
    )
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    private var engine: UpdateEngine? = null
    private var installer: UpdateInstaller? = null
    private var prefs: LinkPrefs? = null

    /** True when this build can update itself. Debug builds never can. */
    val supported: Boolean get() = _status.value.supported

    /** The persisted "check for updates automatically" preference. */
    var autoCheck: Boolean
        get() = prefs?.autoCheckUpdates ?: true
        set(value) {
            prefs?.autoCheckUpdates = value
            engine?.setAutoCheck(value)
        }

    fun start(context: Context) {
        if (engine != null) return
        val app = context.applicationContext
        val stored = LinkPrefs(app)
        prefs = stored
        installer = UpdateInstaller(app)

        val supported = !BuildConfig.DEBUG
        val client = if (supported) GitHubUpdateClient() else null
        val created = UpdateEngine(
            UpdateEngineOptions(
                mode = if (supported) UpdateMode.Installed else UpdateMode.Disabled,
                currentVersion = BuildConfig.VERSION_NAME,
                autoCheck = stored.autoCheckUpdates,
                client = client,
                updatesDir = File(app.filesDir, UPDATES_DIR),
                scope = scope,
                onStatus = { _status.value = it },
                notify = { title, body -> notifyReady(app, title, body) },
                verifySignature = { apk -> verifySignature(app, apk) },
                lastCheckAt = { stored.lastUpdateCheckAt },
                recordCheckAt = { at -> stored.lastUpdateCheckAt = at },
            ),
        )
        engine = created
        _status.value = created.status.value
        if (supported) createChannel(app)
        created.start()
        LinkStatus.log("Update", "Updater started for ${BuildConfig.VERSION_NAME}")
    }

    fun stop() {
        engine?.stop()
    }

    /** A manual check. It runs even when automatic checks are off. */
    suspend fun check() {
        engine?.check(manual = true)
    }

    /** Re-downloads the release found by the last check. */
    suspend fun download() {
        engine?.download()
    }

    /** Opens the system installer for the verified APK. */
    fun install(activity: Activity?): InstallResult {
        val apk = engine?.readyApk ?: return InstallResult.NotReady
        return installer?.install(activity, apk) ?: InstallResult.NotReady
    }

    /** Opens the per-app screen where installs from LinkPhone are allowed. */
    fun openInstallPermissionSettings(activity: Activity?): Boolean =
        installer?.openInstallPermissionSettings(activity) ?: false

    private fun verifySignature(context: Context, apk: File): String? {
        val candidate = ApkSignature.ofArchive(context, apk)
            ?: return "The downloaded update is not a valid Android package."
        if (!sameSigner(ApkSignature.installed(context), candidate)) {
            return "This update is signed with a different key than the installed app. " +
                "Uninstall LinkPhone and install the new version manually."
        }
        return null
    }

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_UPDATES,
            "Updates",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Downloaded app updates"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    private fun notifyReady(context: Context, title: String, body: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_link)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    const val CHANNEL_UPDATES = "updates"
    const val NOTIFICATION_ID = 2001
    private const val UPDATES_DIR = "updates"
}
