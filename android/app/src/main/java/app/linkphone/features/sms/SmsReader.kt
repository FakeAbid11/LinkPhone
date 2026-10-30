package app.linkphone.features.sms

import android.annotation.SuppressLint
import android.content.Context
import android.database.sqlite.SQLiteException
import android.net.Uri
import app.linkphone.core.model.SmsMessageDto
import app.linkphone.core.util.FeaturePermissionException
import app.linkphone.core.util.LinkStatus
import app.linkphone.features.Permissions

class SmsReader(private val context: Context) {
    private val resolver = context.contentResolver

    @SuppressLint("MissingPermission")
    fun listMessages(limit: Int = 500): List<SmsMessageDto> {
        if (!Permissions.sms(context)) {
            val missing = Permissions.smsMissing(context)
            throw FeaturePermissionException(
                if (missing.isEmpty()) {
                    "SMS permission is not granted"
                } else {
                    "Allow LinkPhone to ${missing.joinToString(" and ")} on the phone"
                },
            )
        }
        val safeLimit = limit.coerceIn(1, 1_000)
        val withSubId = arrayOf(
            "_id", "address", "body", "date", "type", "read", "sub_id",
        )
        val withoutSubId = arrayOf("_id", "address", "body", "date", "type", "read")

        val cursor = try {
            resolver.query(CONTENT_URI, withSubId, null, null, "date DESC")
        } catch (error: SQLiteException) {
            LinkStatus.log("Sms", "Falling back to a query without sub_id")
            resolver.query(CONTENT_URI, withoutSubId, null, null, "date DESC")
        } ?: throw IllegalStateException("SMS provider is unavailable")

        val messages = ArrayList<SmsMessageDto>(safeLimit)
        cursor.use {
            val idIndex = it.getColumnIndexOrThrow("_id")
            val addressIndex = it.getColumnIndexOrThrow("address")
            val bodyIndex = it.getColumnIndexOrThrow("body")
            val dateIndex = it.getColumnIndexOrThrow("date")
            val typeIndex = it.getColumnIndexOrThrow("type")
            val readIndex = it.getColumnIndexOrThrow("read")
            val subIndex = it.getColumnIndex("sub_id")

            while (it.moveToNext() && messages.size < safeLimit) {
                val address = it.getString(addressIndex)?.trim().orEmpty()
                val body = it.getString(bodyIndex).orEmpty()
                if (address.isEmpty() || body.isEmpty()) continue
                val type = it.getInt(typeIndex)
                if (type !in 1..6) continue
                messages += SmsMessageDto(
                    id = it.getLong(idIndex).toString(),
                    address = address,
                    body = body,
                    date = it.getLong(dateIndex),
                    type = type,
                    read = it.getInt(readIndex) != 0,
                    subId = if (subIndex >= 0 && !it.isNull(subIndex)) it.getInt(subIndex) else null,
                )
            }
        }
        return messages
    }

    private companion object {
        val CONTENT_URI: Uri = Uri.parse("content://sms")
    }
}
