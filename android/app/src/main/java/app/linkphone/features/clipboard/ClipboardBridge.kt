package app.linkphone.features.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object ClipboardBridge {
    fun read(context: Context): String? {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = manager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
    }

    fun write(context: Context, text: String): Boolean {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
        return runCatching {
            manager.setPrimaryClip(ClipData.newPlainText("LinkPhone", text))
            true
        }.getOrDefault(false)
    }
}
