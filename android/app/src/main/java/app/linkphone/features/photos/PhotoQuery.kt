package app.linkphone.features.photos

import android.provider.MediaStore

internal data class PhotoSelection(
    val selection: String,
    val args: Array<String>,
)

internal object PhotoQuery {
    const val DATE_EXPRESSION = "COALESCE(DATE_ADDED, DATE_MODIFIED, 0)"
    const val SORT_ORDER = "$DATE_EXPRESSION DESC, ${MediaStore.Images.Media._ID} DESC"

    fun selection(
        bucket: String?,
        beforeDateMs: Long?,
        beforeId: String?,
    ): PhotoSelection {
        val clauses = mutableListOf("${MediaStore.Images.Media.IS_PENDING} = 0")
        val args = mutableListOf<String>()

        if (!bucket.isNullOrBlank()) {
            when {
                bucket.startsWith("v:") -> {
                    val rest = bucket.removePrefix("v:")
                    val separator = rest.indexOf(':')
                    if (separator > 0) {
                        val volume = rest.substring(0, separator)
                        val bucketId = rest.substring(separator + 1)
                        if (volume.isNotBlank() && bucketId.isNotBlank()) {
                            clauses += "${MediaStore.Images.Media.VOLUME_NAME} = ?"
                            args += volume
                            clauses += "${MediaStore.Images.Media.BUCKET_ID} = ?"
                            args += bucketId
                        }
                    }
                }

                bucket.startsWith("b:") -> {
                    val bucketId = bucket.removePrefix("b:")
                    if (bucketId.isNotBlank()) {
                        clauses += "${MediaStore.Images.Media.BUCKET_ID} = ?"
                        args += bucketId
                    }
                }

                bucket.startsWith("name:") -> {
                    val name = bucket.removePrefix("name:")
                    if (name.isNotBlank()) {
                        clauses += "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
                        args += name
                        clauses += "${MediaStore.Images.Media.BUCKET_ID} IS NULL"
                    }
                }
            }
        }

        val dateSeconds = beforeDateMs?.takeIf { it > 0 }?.div(1_000L)
        val id = beforeId?.toLongOrNull()?.takeIf { it > 0 }
        if (dateSeconds != null && id != null) {
            clauses += "($DATE_EXPRESSION < ? OR ($DATE_EXPRESSION = ? AND " +
                "${MediaStore.Images.Media._ID} < ?))"
            args += dateSeconds.toString()
            args += dateSeconds.toString()
            args += id.toString()
        }

        return PhotoSelection(clauses.joinToString(" AND "), args.toTypedArray())
    }
}
