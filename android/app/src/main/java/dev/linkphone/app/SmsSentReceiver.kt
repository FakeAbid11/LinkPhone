package dev.linkphone.app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Collects the per-part SmsManager sent results and reports a single outcome to
 * the PC. RESULT_OK means the message was handed to the network, not delivered.
 */
class SmsSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val tempId = intent.getStringExtra(EXTRA_TEMP_ID) ?: return
        val total = intent.getIntExtra(EXTRA_PARTS, 1).coerceIn(1, 20)
        val part = intent.getIntExtra(EXTRA_PART, 0)
        val ok = resultCode == Activity.RESULT_OK
        val outcome = track(tempId, total, part, ok, if (ok) null else errorText(resultCode)) ?: return
        LinkCore.clientOrNull()?.sendSmsResult(tempId, outcome.first, outcome.second)
    }

    private fun track(
        tempId: String,
        total: Int,
        part: Int,
        ok: Boolean,
        error: String?
    ): Pair<Boolean, String?>? {
        val state = pending.getOrPut(tempId) { PartState(total) }
        if (state.total != total) return null
        synchronized(state) {
            if (part !in 0 until total || state.done[part]) return null
            state.done[part] = true
            if (!ok) {
                state.failed = true
                if (state.error == null) state.error = error
            }
            if (!state.done.all { it }) return null
        }
        pending.remove(tempId)
        return !state.failed to state.error
    }

    private fun errorText(code: Int): String = when (code) {
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "The phone could not send the message"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "No mobile service"
        SmsManager.RESULT_ERROR_NULL_PDU -> "The phone rejected the message"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "The radio is off"
        else -> "SMS send failed (code $code)"
    }

    private class PartState(val total: Int) {
        val done = BooleanArray(total)
        var failed = false
        var error: String? = null
    }

    companion object {
        const val ACTION = "dev.linkphone.app.SMS_SENT"
        const val EXTRA_TEMP_ID = "tempId"
        const val EXTRA_PART = "part"
        const val EXTRA_PARTS = "parts"

        private val pending = ConcurrentHashMap<String, PartState>()
    }
}
