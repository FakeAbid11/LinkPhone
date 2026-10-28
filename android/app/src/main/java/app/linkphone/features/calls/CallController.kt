package app.linkphone.features.calls

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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

class CallController(
    private val context: Context,
    private val onState: (CallState) -> Unit,
) {
    private val telephony: TelephonyManager? =
        context.getSystemService(TelephonyManager::class.java)
    private val telecom: TelecomManager? =
        context.getSystemService(TelecomManager::class.java)
    private var listener: PhoneStateListener? = null

    fun isSupported(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

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
                val number = phoneNumber?.takeIf { it.isNotBlank() }
                onState(
                    CallState(
                        state = mapped,
                        number = number,
                        contactName = number?.let(::contactName),
                        timestamp = System.currentTimeMillis(),
                    ),
                )
            }
        }
        try {
            manager.listen(stateListener, PhoneStateListener.LISTEN_CALL_STATE)
            listener = stateListener
        } catch (error: SecurityException) {
            LinkStatus.log("Calls", "Call-state listener needs phone permission")
        }
    }

    @Suppress("DEPRECATION")
    fun stop() {
        val current = listener ?: return
        runCatching { telephony?.listen(current, PhoneStateListener.LISTEN_NONE) }
        listener = null
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
}
