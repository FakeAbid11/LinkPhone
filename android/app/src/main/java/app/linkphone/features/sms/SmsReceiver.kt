package app.linkphone.features.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.linkphone.core.model.SmsChanged
import app.linkphone.core.net.ConnectionHub
import app.linkphone.core.protocol.ProtocolCodec
import app.linkphone.core.protocol.Types
import app.linkphone.core.util.LinkStatus

/**
 * The provider is the source of truth. This receiver only nudges an active PC
 * session to refresh, so missed broadcasts never lose messages.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        LinkStatus.log("Sms", "SMS_RECEIVED broadcast")
        if (!ConnectionHub.isConnected()) return
        ConnectionHub.sendEvent(
            Types.SMS_CHANGED,
            ProtocolCodec.data(SmsChanged(System.currentTimeMillis())),
        )
    }
}
