package app.linkphone.ui

import android.Manifest
import app.linkphone.features.Permissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPermissionsTest {
    @Test
    fun grantAllCoversEveryFeaturePermission() {
        val expected = buildList {
            addAll(Permissions.smsPermissions())
            addAll(Permissions.callPermissions())
            addAll(Permissions.optionalCallPermissions())
            addAll(Permissions.photoPermissions())
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        for (permission in expected) {
            assertTrue(
                "$permission is asked for by a feature but missing from grant all",
                permission in OnboardingPermissions.grantAll,
            )
        }
    }

    @Test
    fun grantAllHasNoDuplicates() {
        assertEquals(
            OnboardingPermissions.grantAll.size,
            OnboardingPermissions.grantAll.toSet().size,
        )
    }

    @Test
    fun everyRowExplainsWhatThePermissionIsFor() {
        val rows = OnboardingPermissions.features + OnboardingPermissions.specialAccess
        assertTrue("no rows", rows.isNotEmpty())
        for (spec in rows) {
            assertTrue("${spec.id} has a blank title", spec.title.isNotBlank())
            assertTrue("${spec.id} has no explanation", spec.detail.isNotBlank())
        }
    }

    @Test
    fun specialAccessRowsDoNotRequestRuntimePermissions() {
        for (spec in OnboardingPermissions.specialAccess) {
            assertEquals(
                "${spec.id} should open a system screen, not a dialog",
                emptyList<String>(),
                OnboardingPermissions.requestFor(spec.id),
            )
        }
    }

    @Test
    fun everyFeatureRowRequestsAtLeastOnePermission() {
        for (spec in OnboardingPermissions.features) {
            assertTrue(
                "${spec.id} asks for nothing",
                OnboardingPermissions.requestFor(spec.id).isNotEmpty(),
            )
        }
    }
}
