package app.linkphone.features.contacts

import android.annotation.SuppressLint
import android.content.Context
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import app.linkphone.core.model.ContactDto
import app.linkphone.core.util.FeaturePermissionException
import app.linkphone.core.util.LinkStatus
import app.linkphone.features.Permissions

class ContactRepository(private val context: Context) {
    @SuppressLint("MissingPermission")
    fun list(limit: Int = 1_000): List<ContactDto> {
        if (!Permissions.contacts(context)) {
            throw FeaturePermissionException("Contacts permission is not granted")
        }
        val safeLimit = limit.coerceIn(1, 2_000)
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
        )
        val contacts = ArrayList<ContactDto>(safeLimit)
        val seen = HashSet<String>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                )
                val nameIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                )
                val numberIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                )
                val normalizedIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                )
                val typeIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                )
                val labelIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.LABEL,
                )

                while (cursor.moveToNext() && contacts.size < safeLimit) {
                    val contactId = cursor.getLong(idIndex)
                    if (contactId <= 0) continue
                    val rawNumber = cursor.getString(numberIndex)?.trim().orEmpty()
                    if (rawNumber.isEmpty()) continue
                    val normalized = if (normalizedIndex >= 0 && !cursor.isNull(normalizedIndex)) {
                        cursor.getString(normalizedIndex)?.trim().orEmpty()
                    } else {
                        ""
                    }
                    val dialable = (normalized.ifBlank {
                        PhoneNumberUtils.normalizeNumber(rawNumber)
                    }).replace(Regex("[^+0-9*#]"), "")
                        .ifBlank { rawNumber }
                    if (dialable.isEmpty()) continue

                    val key = "$contactId|$dialable"
                    if (!seen.add(key)) continue

                    val name = cursor.getString(nameIndex)?.trim().orEmpty()
                        .ifBlank { rawNumber }
                    val type = cursor.getInt(typeIndex)
                    val customLabel = if (labelIndex >= 0 && !cursor.isNull(labelIndex)) {
                        cursor.getString(labelIndex)
                    } else {
                        null
                    }
                    val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        context.resources,
                        type,
                        customLabel,
                    )?.toString()?.take(40)

                    contacts += ContactDto(
                        id = contactId.toString(),
                        name = name.take(120),
                        number = dialable.take(40),
                        label = label,
                    )
                }
            }
        } catch (error: SecurityException) {
            throw FeaturePermissionException("Contacts permission is not granted")
        } catch (error: Exception) {
            LinkStatus.log("Contacts", "Contact query failed: ${error.javaClass.simpleName}")
        }
        return contacts
    }
}
