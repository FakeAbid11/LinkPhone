package app.linkphone.core.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogRing {
    private const val MAX_LINES = 300
    private val buffer = ArrayDeque<String>()
    private val lock = Any()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun add(tag: String, message: String) {
        val safeMessage = message.replace('\n', ' ').take(500)
        synchronized(lock) {
            val line = "${timeFormat.format(Date())} [$tag] $safeMessage"
            buffer.addLast(line)
            while (buffer.size > MAX_LINES) buffer.removeFirst()
        }
        Log.d("LinkPhone/$tag", safeMessage)
    }

    fun snapshot(): List<String> = synchronized(lock) { buffer.toList() }

    fun clear() {
        synchronized(lock) { buffer.clear() }
    }
}
