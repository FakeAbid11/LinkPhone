package app.linkphone.ui

import android.Manifest
import app.linkphone.features.Permissions

/** The permission rows on onboarding step 2. */
enum class OnboardingPermission {
    Messages,
    Calls,
    Contacts,
    Photos,
    Notifications,
    NotificationAccess,
    RemoteTaps,
    Battery,
}

data class OnboardingPermissionSpec(
    val id: OnboardingPermission,
    val title: String,
    val detail: String,
)

/**
 * What each permission is for. Kept free of Android context so the specs and
 * the "grant all" list can be checked by a plain unit test.
 */
object OnboardingPermissions {

    /** Normal runtime permissions, each asked with a system dialog. */
    val features: List<OnboardingPermissionSpec> = listOf(
        OnboardingPermissionSpec(
            OnboardingPermission.Messages,
            "Messages",
            "Read conversations and send SMS with the PC keyboard.",
        ),
        OnboardingPermissionSpec(
            OnboardingPermission.Calls,
            "Calls",
            "See calls, dial, and choose a SIM. Audio stays on the phone.",
        ),
        OnboardingPermissionSpec(
            OnboardingPermission.Contacts,
            "Contacts",
            "Show names when you search for a caller on the PC.",
        ),
        OnboardingPermissionSpec(
            OnboardingPermission.Photos,
            "Photos",
            "Browse camera-roll images. Android 14 can limit this to selected photos.",
        ),
        OnboardingPermissionSpec(
            OnboardingPermission.Notifications,
            "Notifications",
            "Show phone notifications on the PC. Android 13+ asks for this.",
        ),
    )

    /** Special access that Android does not grant with a normal dialog. */
    val specialAccess: List<OnboardingPermissionSpec> = listOf(
        OnboardingPermissionSpec(
            OnboardingPermission.NotificationAccess,
            "Notification access",
            "Lets LinkPhone read notifications to mirror them. This opens a system screen.",
        ),
        OnboardingPermissionSpec(
            OnboardingPermission.RemoteTaps,
            "Remote taps",
            "Accessibility service so taps on the mirrored screen reach the phone.",
        ),
        OnboardingPermissionSpec(
            OnboardingPermission.Battery,
            "Battery optimisation",
            "Keeps the connection alive in the background. Recommended, not required.",
        ),
    )

    /** The permissions to ask for on a row's own button. */
    fun requestFor(id: OnboardingPermission): List<String> = when (id) {
        OnboardingPermission.Messages -> Permissions.smsPermissions().toList()
        OnboardingPermission.Calls ->
            (Permissions.callPermissions() + Permissions.optionalCallPermissions()).toList()
        OnboardingPermission.Contacts -> listOf(Manifest.permission.READ_CONTACTS)
        OnboardingPermission.Photos -> Permissions.photoPermissions().toList()
        OnboardingPermission.Notifications -> listOf(Manifest.permission.POST_NOTIFICATIONS)
        OnboardingPermission.NotificationAccess,
        OnboardingPermission.RemoteTaps,
        OnboardingPermission.Battery -> emptyList()
    }

    /**
     * Every runtime permission the app can ask for. One request shows Android's
     * dialogs for each permission group in turn.
     */
    val grantAll: List<String> = features
        .flatMap { requestFor(it.id) }
        .distinct()
}
