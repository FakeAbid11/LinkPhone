package app.linkphone.features.sms

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper

class SmsObserver(
    context: Context,
    private val onChange: () -> Unit,
) : ContentObserver(Handler(Looper.getMainLooper())) {
    private val resolver = context.contentResolver
    private var registered = false

    fun start() {
        if (registered) return
        runCatching {
            resolver.registerContentObserver(CONTENT_URI, true, this)
            registered = true
        }
    }

    fun stop() {
        if (!registered) return
        runCatching { resolver.unregisterContentObserver(this) }
        registered = false
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        onChange()
    }

    private companion object {
        val CONTENT_URI: Uri = Uri.parse("content://sms")
    }
}
