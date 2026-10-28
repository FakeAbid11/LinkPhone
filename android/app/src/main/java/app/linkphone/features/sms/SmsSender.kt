package app.linkphone.features.sms

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import app.linkphone.core.model.SmsSendResult
import app.linkphone.core.util.FeaturePermissionException
import app.linkphone.core.util.SafeInput
import app.linkphone.features.Permissions

class SmsSender(private val context: Context) {
    @SuppressLint("MissingPermission")
    fun send(addressRaw: String, body: String, subId: Int?): SmsSendResult {
        if (!Permissions.has(context, android.Manifest.permission.SEND_SMS)) {
            throw FeaturePermissionException("SEND_SMS permission is not granted")
        }
        val address = SafeInput.normalizeDialNumber(addressRaw)
            ?: throw IllegalArgumentException("Destination must be a phone number")
        if (body.isBlank()) throw IllegalArgumentException("Message is empty")
        if (body.length > 5_000) throw IllegalArgumentException("Message is longer than 5000 characters")

        val manager = smsManager(subId)
        val parts = manager.divideMessage(body)
        if (parts.isEmpty()) throw IllegalArgumentException("Message could not be encoded")
        if (parts.size > 10) throw IllegalArgumentException("Message exceeds 10 SMS parts")

        if (parts.size == 1) {
            manager.sendTextMessage(address, null, body, null, null)
        } else {
            manager.sendMultipartTextMessage(address, null, parts, null, null)
        }
        return SmsSendResult(
            accepted = true,
            parts = parts.size,
            subId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) subId else null,
        )
    }

    @Suppress("DEPRECATION")
    private fun smsManager(subId: Int?): SmsManager {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && subId != null) {
            val base = context.getSystemService(SmsManager::class.java)
            if (base != null) {
                return runCatching { base.createForSubscriptionId(subId) }.getOrDefault(base)
            }
        }
        return SmsManager.getDefault()
    }
}
