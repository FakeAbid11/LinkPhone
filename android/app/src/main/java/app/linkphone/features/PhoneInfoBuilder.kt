package app.linkphone.features

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import app.linkphone.core.model.Capabilities
import app.linkphone.core.model.PhoneInfo
import app.linkphone.core.model.SimInfo
import app.linkphone.core.util.LinkStatus

object PhoneInfoBuilder {
    fun build(context: Context, appVersion: String): PhoneInfo {
        val capabilities = mutableListOf<String>()
        if (Permissions.sms(context)) capabilities += Capabilities.SMS
        if (Permissions.notificationAccess(context)) capabilities += Capabilities.NOTIFICATIONS
        if (Permissions.photos(context)) {
            capabilities += Capabilities.PHOTOS
            if (Permissions.photosPartial(context)) capabilities += Capabilities.PHOTOS_PARTIAL
        }
        if (Permissions.calls(context) && hasTelephony(context)) {
            capabilities += Capabilities.CALLS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                capabilities += Capabilities.CALLS_SIM_SELECTION
            }
        }
        if (Permissions.canAnswerCalls(context)) capabilities += Capabilities.CALLS_ANSWER
        if (Permissions.callerId(context)) capabilities += Capabilities.CALLS_CALLER_ID
        capabilities += Capabilities.MIRROR
        if (Permissions.accessibility(context)) capabilities += Capabilities.MIRROR_INPUT
        capabilities += Capabilities.CLIPBOARD
        capabilities += Capabilities.HANDOFF

        return PhoneInfo(
            model = Build.MODEL?.trim().orEmpty().ifEmpty { "Android" },
            androidVersion = Build.VERSION.RELEASE ?: "unknown",
            appVersion = appVersion,
            capabilities = capabilities.distinct(),
            subscriptions = subscriptions(context),
        )
    }

    fun hasTelephony(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_GSM)

    private fun subscriptions(context: Context): List<SimInfo> {
        if (!Permissions.has(context, android.Manifest.permission.READ_PHONE_STATE)) {
            return emptyList()
        }
        return try {
            val manager = context.getSystemService(SubscriptionManager::class.java)
                ?: return emptyList()
            manager.activeSubscriptionInfoList.orEmpty().map { info ->
                SimInfo(
                    subscriptionId = info.subscriptionId,
                    label = info.displayName?.toString()?.take(40)
                        ?: "SIM ${info.simSlotIndex + 1}",
                    slotIndex = info.simSlotIndex,
                )
            }
        } catch (error: SecurityException) {
            LinkStatus.log("Phone", "SIM list needs phone permission")
            emptyList()
        }
    }
}
