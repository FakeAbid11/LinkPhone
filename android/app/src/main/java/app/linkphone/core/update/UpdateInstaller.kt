package app.linkphone.core.update

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import app.linkphone.core.crypto.toHex
import java.io.File
import java.security.MessageDigest

sealed interface InstallResult {
    /** The system package installer was opened. */
    data object Launched : InstallResult

    /** The user has not allowed LinkPhone to install packages yet. */
    data object NeedsPermission : InstallResult

    /** No verified update is ready. */
    data object NotReady : InstallResult

    /** Something went wrong; the message explains it. */
    data class Failed(val message: String) : InstallResult
}

/**
 * The certificates a package is signed with. [current] are the certificates
 * that sign the APK now; [history] is the rotation lineage, oldest first.
 */
data class SignerInfo(
    val current: Set<String>,
    val history: Set<String>,
)

/**
 * True when [candidate] carries a key that this app is signed with, or a key
 * that descends from the key this app is signed with. A retired key of ours is
 * deliberately not accepted: that would allow a rollback.
 */
fun sameSigner(installed: SignerInfo, candidate: SignerInfo): Boolean {
    if (installed.current.isEmpty() || candidate.current.isEmpty()) return false
    if (candidate.current.any { it in installed.current }) return true
    return installed.current.any { ours -> candidate.history.any { it == ours } }
}

/** Reads the signing certificates of a package or an APK archive. */
object ApkSignature {

    /** The certificates of the running app. */
    fun installed(context: Context): SignerInfo {
        val info = runCatching {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
        }.getOrNull() ?: return SignerInfo(emptySet(), emptySet())
        return fromSigningInfo(info.signingInfo)
    }

    /** The certificates of an APK on disk, or null when it cannot be verified. */
    fun ofArchive(context: Context, apk: File): SignerInfo? {
        val info = runCatching {
            context.packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
        }.getOrNull() ?: return null
        val signing = fromSigningInfo(info.signingInfo)
        return signing.takeIf { it.current.isNotEmpty() }
    }

    private fun fromSigningInfo(signing: android.content.pm.SigningInfo?): SignerInfo {
        if (signing == null) return SignerInfo(emptySet(), emptySet())
        val current = signing.apkContentsSigners.orEmpty().map { it.digestHex() }.toSet()
        val history = signing.signingCertificateHistory.orEmpty().map { it.digestHex() }.toSet()
        return SignerInfo(current, history)
    }

    private fun android.content.pm.Signature.digestHex(): String =
        MessageDigest.getInstance("SHA-256").digest(toByteArray()).toHex()
}

/** Hands a verified APK to the Android package installer. */
class UpdateInstaller(private val context: Context) {

    /** True when the user already allowed LinkPhone to install packages. */
    fun canRequestInstalls(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Opens the per-app "install unknown apps" screen for LinkPhone. */
    fun openInstallPermissionSettings(activity: Activity?): Boolean {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            (activity ?: context).startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /** The content URI the package installer may read. */
    fun uriFor(apk: File): Uri? = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
    }.getOrNull()

    /** Opens the system installer for [apk]. */
    fun install(activity: Activity?, apk: File): InstallResult {
        if (!apk.exists()) return InstallResult.NotReady
        if (!canRequestInstalls()) return InstallResult.NeedsPermission
        val uri = uriFor(apk) ?: return InstallResult.Failed("Could not open the downloaded update.")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("LinkPhone update", uri)
        }
        return runCatching {
            (activity ?: context).startActivity(intent)
            InstallResult.Launched
        }.getOrElse {
            InstallResult.Failed("No app on this device can install the update.")
        }
    }

    companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
