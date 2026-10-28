package app.linkphone.features

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import app.linkphone.features.mirror.LinkAccessibilityService
import app.linkphone.features.notifications.LinkNotificationListener

object Permissions {
    fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun notifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            has(context, Manifest.permission.POST_NOTIFICATIONS)

    fun sms(context: Context): Boolean =
        has(context, Manifest.permission.READ_SMS) &&
            has(context, Manifest.permission.SEND_SMS) &&
            has(context, Manifest.permission.RECEIVE_SMS)

    fun photos(context: Context): Boolean {
        val full = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            has(context, Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            has(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return full || photosPartial(context)
    }

    fun photosPartial(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            has(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) &&
            !has(context, Manifest.permission.READ_MEDIA_IMAGES)

    fun calls(context: Context): Boolean =
        has(context, Manifest.permission.READ_PHONE_STATE) &&
            has(context, Manifest.permission.CALL_PHONE)

    fun canAnswerCalls(context: Context): Boolean =
        has(context, Manifest.permission.ANSWER_PHONE_CALLS)

    fun callerId(context: Context): Boolean =
        has(context, Manifest.permission.READ_CALL_LOG)

    fun contacts(context: Context): Boolean =
        has(context, Manifest.permission.READ_CONTACTS)

    fun camera(context: Context): Boolean =
        has(context, Manifest.permission.CAMERA)

    fun notificationAccess(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.isNotificationListenerAccessGranted(
            ComponentName(context, LinkNotificationListener::class.java),
        )
    }

    fun accessibility(context: Context): Boolean = LinkAccessibilityService.isConnected

    fun ignoringBatteryOptimizations(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun smsPermissions(): Array<String> = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
    )

    fun callPermissions(): Array<String> = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.CALL_PHONE,
    )

    fun optionalCallPermissions(): Array<String> = arrayOf(
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.READ_CALL_LOG,
    )

    fun photoPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
}
