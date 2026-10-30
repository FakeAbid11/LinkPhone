package app.linkphone.core.prefs

import android.content.Context
import android.os.Build
import app.linkphone.core.crypto.normalizeFingerprint
import java.util.UUID

data class PairedPc(
    val pcId: String,
    val pcName: String,
    val host: String,
    val port: Int,
    val fingerprint: String,
)

class LinkPrefs(context: Context) {
    private val store = context.applicationContext
        .getSharedPreferences("linkphone.prefs", Context.MODE_PRIVATE)
    private val lock = Any()

    val phoneId: String
        get() = synchronized(lock) {
            store.getString(KEY_PHONE_ID, null) ?: UUID.randomUUID().toString().also {
                store.edit().putString(KEY_PHONE_ID, it).apply()
            }
        }

    var phoneName: String
        get() = store.getString(KEY_PHONE_NAME, null) ?: defaultName()
        set(value) {
            store.edit().putString(KEY_PHONE_NAME, value.trim().take(80)).apply()
        }

    var autoConnect: Boolean
        get() = store.getBoolean(KEY_AUTO_CONNECT, true)
        set(value) {
            store.edit().putBoolean(KEY_AUTO_CONNECT, value).apply()
        }

    var onboardingDone: Boolean
        get() = store.getBoolean(KEY_ONBOARDING, false)
        set(value) {
            store.edit().putBoolean(KEY_ONBOARDING, value).apply()
        }

    var autoCheckUpdates: Boolean
        get() = store.getBoolean(KEY_AUTO_CHECK_UPDATES, true)
        set(value) {
            store.edit().putBoolean(KEY_AUTO_CHECK_UPDATES, value).apply()
        }

    /** Material You: follow the wallpaper palette on Android 12+. */
    var dynamicColor: Boolean
        get() = store.getBoolean(KEY_DYNAMIC_COLOR, true)
        set(value) {
            store.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
        }

    /** When the updater last contacted GitHub, in epoch milliseconds. */
    var lastUpdateCheckAt: Long
        get() = store.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) {
            store.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()
        }

    var desiredConnected: Boolean
        get() = if (store.contains(KEY_DESIRED_CONNECTED)) {
            store.getBoolean(KEY_DESIRED_CONNECTED, false)
        } else {
            autoConnect
        }
        set(value) {
            store.edit().putBoolean(KEY_DESIRED_CONNECTED, value).apply()
        }

    val pairedPc: PairedPc?
        get() {
            val pcId = store.getString(KEY_PC_ID, null) ?: return null
            val host = store.getString(KEY_PC_HOST, null) ?: return null
            val port = store.getInt(KEY_PC_PORT, 0)
            val fingerprint = normalizeFingerprint(store.getString(KEY_PC_FINGERPRINT, null))
                ?: return null
            if (port !in 1..65535) return null
            return PairedPc(
                pcId = pcId,
                pcName = store.getString(KEY_PC_NAME, null) ?: "PC",
                host = host,
                port = port,
                fingerprint = fingerprint,
            )
        }

    val isPaired: Boolean get() = pairedPc != null

    fun savePaired(pc: PairedPc) {
        store.edit()
            .putString(KEY_PC_ID, pc.pcId)
            .putString(KEY_PC_NAME, pc.pcName)
            .putString(KEY_PC_HOST, pc.host)
            .putInt(KEY_PC_PORT, pc.port)
            .putString(KEY_PC_FINGERPRINT, normalizeFingerprint(pc.fingerprint) ?: pc.fingerprint)
            .apply()
    }

    fun updateTarget(host: String, port: Int) {
        if (host.isBlank() || port !in 1..65535) return
        store.edit()
            .putString(KEY_PC_HOST, host)
            .putInt(KEY_PC_PORT, port)
            .apply()
    }

    fun clearPairing() {
        store.edit()
            .remove(KEY_PC_ID)
            .remove(KEY_PC_NAME)
            .remove(KEY_PC_HOST)
            .remove(KEY_PC_PORT)
            .remove(KEY_PC_FINGERPRINT)
            .remove(KEY_DESIRED_CONNECTED)
            .apply()
    }

    /**
     * Whether the runtime dialog for [key] has already been shown. Persisted so
     * the "Open settings" hint stays correct after Android kills the process.
     */
    fun wasPermissionAsked(key: String): Boolean =
        store.getBoolean(KEY_ASKED_PREFIX + key, false)

    fun markPermissionAsked(key: String) {
        store.edit().putBoolean(KEY_ASKED_PREFIX + key, true).apply()
    }

    private fun defaultName(): String {
        val model = Build.MODEL?.trim().orEmpty()
        return model.ifEmpty { "Android phone" }.take(80)
    }

    private companion object {
        const val KEY_PHONE_ID = "phone.id"
        const val KEY_PHONE_NAME = "phone.name"
        const val KEY_AUTO_CONNECT = "auto.connect"
        const val KEY_DESIRED_CONNECTED = "connection.desired"
        const val KEY_ONBOARDING = "onboarding.done"
        const val KEY_AUTO_CHECK_UPDATES = "update.autoCheck"
        const val KEY_DYNAMIC_COLOR = "theme.dynamicColor"
        const val KEY_LAST_UPDATE_CHECK = "update.lastCheckAt"
        const val KEY_PC_ID = "pc.id"
        const val KEY_PC_NAME = "pc.name"
        const val KEY_PC_HOST = "pc.host"
        const val KEY_PC_PORT = "pc.port"
        const val KEY_PC_FINGERPRINT = "pc.fingerprint"
        const val KEY_ASKED_PREFIX = "permission.asked."
    }
}
