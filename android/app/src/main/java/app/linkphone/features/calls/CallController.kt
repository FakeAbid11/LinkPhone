package app.linkphone.features.calls

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.ContactsContract
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import app.linkphone.core.model.CallState
import app.linkphone.core.util.FeaturePermissionException
import app.linkphone.core.util.LinkStatus
import app.linkphone.core.util.SafeInput
import app.linkphone.features.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onState: (CallState) -> Unit,
) {
    private val telephony: TelephonyManager? =
        context.getSystemService(TelephonyManager::class.java)
    private val telecom: TelecomManager? =
        context.getSystemService(TelecomManager::class.java)
    private val audio: AudioManager? =
        context.getSystemService(AudioManager::class.java)
    private var listener: PhoneStateListener? = null
    private var speakerOn = false
    private var lastState: CallState? = null

    /** Invalidates a contact lookup that started before the last state change. */
    private var generation = 0

    fun isSupported(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_GSM)

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun start() {
        if (listener != null) return
        if (!Permissions.calls(context) || !isSupported()) return
        val manager = telephony ?: return
        val stateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                val mapped = when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> "ringing"
                    TelephonyManager.CALL_STATE_OFFHOOK -> "offhook"
                    TelephonyManager.CALL_STATE_IDLE -> "idle"
                    else -> "unknown"
                }
                if (mapped == "idle") resetSpeakerRoute()
                val number = phoneNumber?.takeIf { it.isNotBlank() }
                val next = CallState(
                    state = mapped,
                    number = number,
                    timestamp = System.currentTimeMillis(),
                    speakerOn = speakerOn,
                )
                lastState = next
                onState(next)
                // The name comes from a content provider, so it is resolved off
                // the main thread and sent as a follow-up update.
                if (number != null) resolveContactName(number, mapped)
            }
        }
        try {
            manager.listen(stateListener, PhoneStateListener.LISTEN_CALL_STATE)
            listener = stateListener
            val initial = runCatching { manager.callState }.getOrNull()
            if (initial != null && initial != TelephonyManager.CALL_STATE_IDLE) {
                val mapped = when (initial) {
                    TelephonyManager.CALL_STATE_RINGING -> "ringing"
                    TelephonyManager.CALL_STATE_OFFHOOK -> "offhook"
                    else -> "unknown"
                }
                val next = CallState(
                    state = mapped,
                    timestamp = System.currentTimeMillis(),
                    speakerOn = speakerOn,
                )
                lastState = next
                onState(next)
            }
        } catch (error: SecurityException) {
            LinkStatus.log("Calls", "Call-state listener needs phone permission")
        }
    }

    @Suppress("DEPRECATION")
    fun stop() {
        generation += 1
        val current = listener
        if (current != null) {
            runCatching { telephony?.listen(current, PhoneStateListener.LISTEN_NONE) }
            listener = null
        }
        resetSpeakerRoute()
        lastState = null
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun placeCall(numberRaw: String, subscriptionId: Int? = null): Result<Unit> {
        if (!Permissions.has(context, Manifest.permission.CALL_PHONE)) {
            return Result.failure(FeaturePermissionException("CALL_PHONE permission is not granted"))
        }
        val number = SafeInput.normalizeDialNumber(numberRaw)
            ?: return Result.failure(IllegalArgumentException("Invalid phone number"))
        if (subscriptionId != null && subscriptionId <= 0) {
            return Result.failure(IllegalArgumentException("Invalid SIM selection"))
        }
        val manager = telecom ?: return Result.failure(IllegalStateException("Telecom is unavailable"))

        val extras = Bundle()
        if (subscriptionId != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return Result.failure(
                    IllegalStateException("Choosing a SIM requires Android 11 or later"),
                )
            }
            val handle = findPhoneAccountForSubscription(subscriptionId)
                ?: return Result.failure(
                    IllegalStateException(
                        "That SIM is not available for calls. Reconnect and try again.",
                    ),
                )
            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
        }

        return runCatching {
            manager.placeCall(Uri.fromParts("tel", number, null), extras)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("MissingPermission")
    private fun findPhoneAccountForSubscription(subscriptionId: Int): PhoneAccountHandle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val manager = telecom ?: return null
        val telephonyManager = telephony ?: return null
        return runCatching {
            val handles = manager.callCapablePhoneAccounts.orEmpty()
            val simHandle = handles.firstOrNull { handle ->
                val account = runCatching { manager.getPhoneAccount(handle) }.getOrNull()
                val isSim = account != null &&
                    (account.capabilities and PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION) != 0
                isSim && telephonyManager.getSubscriptionId(handle) == subscriptionId
            }
            simHandle ?: handles.firstOrNull { handle ->
                telephonyManager.getSubscriptionId(handle) == subscriptionId
            }
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun answer(): Result<Unit> {
        if (!Permissions.canAnswerCalls(context)) {
            return Result.failure(
                FeaturePermissionException("ANSWER_PHONE_CALLS permission is not granted"),
            )
        }
        val manager = telecom ?: return Result.failure(IllegalStateException("Telecom is unavailable"))
        return runCatching { manager.acceptRingingCall() }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun decline(): Result<Unit> {
        if (!Permissions.canAnswerCalls(context)) {
            return Result.failure(
                FeaturePermissionException("ANSWER_PHONE_CALLS permission is not granted"),
            )
        }
        val manager = telecom ?: return Result.failure(IllegalStateException("Telecom is unavailable"))
        return runCatching { manager.endCall() }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun setSpeaker(enabled: Boolean): Result<Unit> {
        if (!Permissions.has(context, Manifest.permission.MODIFY_AUDIO_SETTINGS)) {
            return Result.failure(
                FeaturePermissionException("MODIFY_AUDIO_SETTINGS permission is not granted"),
            )
        }
        if (lastState?.state != "offhook") {
            return Result.failure(IllegalStateException("There is no active call"))
        }
        val manager = audio ?: return Result.failure(IllegalStateException("Audio is unavailable"))
        var actual = speakerOn
        val changed = runCatching {
            var deviceSelected = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (enabled) {
                    val speaker = manager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speaker != null) {
                        deviceSelected = runCatching {
                            manager.setCommunicationDevice(speaker)
                        }.getOrDefault(false)
                    }
                    // Some OEM telecom stacks still honor the legacy route request.
                    runCatching { manager.isSpeakerphoneOn = true }
                } else {
                    runCatching { manager.clearCommunicationDevice() }
                    runCatching { manager.isSpeakerphoneOn = false }
                }
            } else {
                manager.isSpeakerphoneOn = enabled
            }

            // The route is applied asynchronously; read it back before reporting success.
            SystemClock.sleep(SPEAKER_SETTLE_MS)
            actual = manager.isSpeakerphoneOn
            if (actual != enabled) {
                error(
                    "Speaker route not applied " +
                        "(requested=$enabled, reported=$actual, deviceSelected=$deviceSelected)",
                )
            }
        }
        return changed.fold(
            onSuccess = {
                speakerOn = enabled
                lastState?.let { onState(it.copy(speakerOn = speakerOn)) }
                Result.success(Unit)
            },
            onFailure = {
                if (!actual) {
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            audio?.clearCommunicationDevice()
                        }
                    }
                    runCatching { audio?.isSpeakerphoneOn = false }
                }
                speakerOn = actual
                lastState?.let { onState(it.copy(speakerOn = speakerOn)) }
                LinkStatus.log("Calls", "Speaker request blocked (reported=$actual)")
                Result.failure(
                    IllegalStateException(
                        "This phone blocked speaker control. Use the phone's in-call screen.",
                        it,
                    ),
                )
            },
        )
    }

    private fun resetSpeakerRoute() {
        if (!speakerOn) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audio?.clearCommunicationDevice()
            }
        }
        runCatching {
            @Suppress("DEPRECATION")
            audio?.isSpeakerphoneOn = false
        }
        speakerOn = false
    }

    /**
     * Resolves the caller's name off the main thread and sends a second
     * call.state update. Skipped when the call has already moved on.
     */
    private fun resolveContactName(number: String, state: String) {
        val current = ++generation
        scope.launch {
            val name = contactName(number) ?: return@launch
            withContext(Dispatchers.Main.immediate) {
                if (current != generation) return@withContext
                val latest = lastState ?: return@withContext
                if (latest.state != state) return@withContext
                if (latest.contactName == name) return@withContext
                val named = latest.copy(contactName = name)
                lastState = named
                onState(named)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun contactName(number: String): String? {
        if (!Permissions.contacts(context)) return null
        return runCatching {
            context.contentResolver.query(
                Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(number),
                ),
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    private companion object {
        const val SPEAKER_SETTLE_MS = 180L
    }
}
