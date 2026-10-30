package app.linkphone.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.linkphone.BuildConfig
import app.linkphone.core.crypto.DeviceIdentity
import app.linkphone.core.crypto.PairingUri
import app.linkphone.core.crypto.formatFingerprint
import app.linkphone.core.crypto.normalizeFingerprint
import app.linkphone.core.net.PairingClient
import app.linkphone.core.net.PairingFailureClassifier
import app.linkphone.core.net.PcDiscovery
import app.linkphone.core.net.PhoneNetworkReader
import app.linkphone.core.net.SubnetReport
import app.linkphone.core.prefs.LinkPrefs
import app.linkphone.core.prefs.PairedPc
import app.linkphone.core.update.AppUpdater
import app.linkphone.core.update.InstallResult
import app.linkphone.core.update.UpdateStatus
import app.linkphone.core.util.ConnectionPolicy
import app.linkphone.core.util.LinkState
import app.linkphone.core.util.LinkStatus
import app.linkphone.service.LinkConnectionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

sealed interface PairingUiState {
    data object Idle : PairingUiState
    data class Working(val message: String) : PairingUiState

    /**
     * @property message the actionable sentence shown as the headline
     * @property detail the raw exception text, for support and for "Copy detail"
     * @property checklist short "what to check" bullets; empty when the PC's own
     *   message already says what to do
     * @property subnet where the PC's address sits relative to this phone's links;
     *   null when the failure was not a routing one, or we could not tell
     */
    data class Error(
        val message: String,
        val detail: String? = null,
        val checklist: List<String> = emptyList(),
        val subnet: SubnetReport? = null,
    ) : PairingUiState

    data class Success(val pc: PairedPc) : PairingUiState
}

enum class Screen { Home, Onboarding, Pair }

class LinkViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = LinkPrefs(application)
    private val identity = DeviceIdentity()
    private val networkReader = PhoneNetworkReader(application)

    val state: StateFlow<LinkState> = LinkStatus.state
    val logs: StateFlow<List<String>> = LinkStatus.logs

    private val _screen = MutableStateFlow(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _paired = MutableStateFlow(prefs.pairedPc)
    val paired: StateFlow<PairedPc?> = _paired.asStateFlow()

    private val _pairing = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val pairing: StateFlow<PairingUiState> = _pairing.asStateFlow()

    private val _permissionRevision = MutableStateFlow(0)
    val permissionRevision: StateFlow<Int> = _permissionRevision.asStateFlow()

    val updateStatus: StateFlow<UpdateStatus> = AppUpdater.status

    private var pairingJob: Job? = null
    private var updateJob: Job? = null

    val appVersion: String = BuildConfig.VERSION_NAME

    init {
        if (!prefs.onboardingDone) {
            if (prefs.isPaired) {
                prefs.onboardingDone = true
            } else {
                _screen.value = Screen.Onboarding
            }
        }
    }

    var autoCheckUpdates: Boolean
        get() = AppUpdater.autoCheck
        set(value) {
            AppUpdater.autoCheck = value
        }

    fun checkForUpdates() {
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            AppUpdater.check()
        }
    }

    fun downloadUpdate() {
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            AppUpdater.download()
        }
    }

    fun installUpdate(activity: Activity?): InstallResult = AppUpdater.install(activity)

    fun openInstallPermissionSettings(activity: Activity?): Boolean =
        AppUpdater.openInstallPermissionSettings(activity)

    fun goHome() {
        _screen.value = Screen.Home
        refresh()
    }

    fun openPair() {
        _screen.value = Screen.Pair
    }

    fun openOnboarding() {
        _screen.value = Screen.Onboarding
    }

    fun completeOnboarding() {
        prefs.onboardingDone = true
        _screen.value = Screen.Home
        refresh()
    }

    fun onScannedPair(raw: String) {
        _screen.value = Screen.Pair
        pair(raw)
    }

    fun refresh() {
        _paired.value = prefs.pairedPc
        _permissionRevision.value += 1
        if (LinkStatus.state.value is LinkState.Connected) {
            LinkConnectionService.refreshInfo(getApplication())
        }
    }

    fun start() {
        if (prefs.isPaired) {
            prefs.desiredConnected = true
            LinkConnectionService.start(getApplication())
        }
    }

    fun shouldAutoStart(): Boolean =
        ConnectionPolicy.shouldAutoStart(
            paired = prefs.isPaired,
            autoConnect = prefs.autoConnect,
            desiredConnected = prefs.desiredConnected,
        ) && (LinkStatus.state.value is LinkState.Stopped ||
            LinkStatus.state.value is LinkState.Connecting)

    fun disconnect() {
        prefs.desiredConnected = false
        LinkConnectionService.disconnect(getApplication())
    }

    fun forget() {
        pairingJob?.cancel()
        prefs.desiredConnected = false
        LinkConnectionService.forget(getApplication())
        prefs.clearPairing()
        _paired.value = null
        _pairing.value = PairingUiState.Idle
        _screen.value = Screen.Home
        refresh()
    }

    fun clearLogs() {
        LinkStatus.logs.value = emptyList()
    }

    fun resetPairingState() {
        _pairing.value = PairingUiState.Idle
    }

    fun pair(raw: String) {
        if (pairingJob?.isActive == true) return
        val info = PairingUri.parse(raw)
        if (info == null) {
            _pairing.value = PairingUiState.Error(
                "That is not a valid LinkPhone pairing code. Check the code and try again.",
            )
            return
        }
        pairingJob = viewModelScope.launch {
            _pairing.value = PairingUiState.Working("Connecting to ${info.host}…")
            // A binder round-trip per network, so never on the main dispatcher.
            // Read once per attempt: the answer cannot change mid-handshake in a
            // way that matters, and re-reading would make the failure message
            // disagree with the log.
            val phone = withContext(Dispatchers.IO) { networkReader.read() }
            LinkStatus.log(
                "Pair",
                "This phone: ${phone.summary()}; code points at ${info.host}:${info.port}" +
                    (if (phone.vpnActive) " (VPN active)" else ""),
            )
            val client = PairingClient(
                identity = identity,
                phoneId = prefs.phoneId,
                phoneName = prefs.phoneName,
                appVersion = BuildConfig.VERSION_NAME,
                discovery = PcDiscovery(getApplication()),
                onLog = { message -> LinkStatus.log("Pair", message) },
            )
            try {
                val pc = client.pair(info) { message ->
                    _pairing.value = PairingUiState.Working(message)
                }
                prefs.savePaired(pc)
                _paired.value = pc
                _pairing.value = PairingUiState.Success(pc)
                LinkStatus.log("Pair", "Paired with ${pc.pcName}")
                LinkConnectionService.start(getApplication())
            } catch (error: Exception) {
                // The raw exception used to be the whole message ("Failed to
                // connect to /192.168.0.111:48921"), which names the layer but
                // not the fix. Classify it, and record it in Diagnostics so a
                // failure that only happens on the user's network leaves a trace.
                val failure = PairingFailureClassifier.classify(error, info.host, info.port, phone)
                LinkStatus.log(
                    "Pair",
                    "Pairing with ${info.host}:${info.port} failed " +
                        "(${failure.kind}, ${failure.subnet?.verdict ?: "unknown"}): " +
                        (failure.detail ?: failure.message),
                )
                _pairing.value = PairingUiState.Error(
                    message = failure.message,
                    detail = failure.detail,
                    checklist = failure.checklist,
                    subnet = failure.subnet,
                )
            }
        }
    }

    fun pairManual(host: String, port: Int, fingerprint: String, token: String) {
        val normalizedFingerprint = normalizeFingerprint(fingerprint)
        if (host.isBlank()) {
            _pairing.value = PairingUiState.Error("Enter the PC address shown in LinkPhone.")
            return
        }
        if (port !in 1..65535) {
            _pairing.value = PairingUiState.Error("Enter a valid port (usually 48921).")
            return
        }
        if (normalizedFingerprint == null) {
            _pairing.value = PairingUiState.Error("The certificate fingerprint must be 64 hex characters.")
            return
        }
        if (token.isBlank()) {
            _pairing.value = PairingUiState.Error("Enter the pairing code shown on the PC.")
            return
        }
        val encodedHost = URLEncoder.encode(host.trim(), Charsets.UTF_8.name())
        val encodedToken = URLEncoder.encode(token.trim(), Charsets.UTF_8.name())
        pair(
            "linkphone://pair?h=$encodedHost&p=$port&f=$normalizedFingerprint&t=$encodedToken",
        )
    }

    fun identityFingerprint(): String = runCatching {
        formatFingerprint(identity.fingerprintHex())
    }.getOrDefault("Unavailable")

    fun wasPermissionAsked(key: String): Boolean = prefs.wasPermissionAsked(key)

    fun markPermissionAsked(key: String) {
        prefs.markPermissionAsked(key)
    }
}
