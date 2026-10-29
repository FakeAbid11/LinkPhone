package app.linkphone.features.photos

internal data class PhotoSelection(
    val selection: String,
    val args: Array<String>,
)

internal object PhotoQuery {
    private const val COLUMN_IS_PENDING = "is_pending"
    private const val COLUMN_VOLUME_NAME = "volume_name"
    private const val COLUMN_BUCKET_ID = "bucket_id"
    private const val COLUMN_BUCKET_DISPLAY_NAME = "bucket_display_name"
    private const val COLUMN_ID = "_id"

    const val DATE_EXPRESSION = "COALESCE(date_added, date_modified, 0)"
    const val SORT_ORDER = "$DATE_EXPRESSION DESC, $COLUMN_ID DESC"

    fun selection(
        bucket: String?,
        beforeDateMs: Long?,
        beforeId: String?,
    ): PhotoSelection {
        val clauses = mutableListOf("$COLUMN_IS_PENDING = 0")
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
                            clauses += "$COLUMN_VOLUME_NAME = ?"
                            args += volume
                            clauses += "$COLUMN_BUCKET_ID = ?"
                            args += bucketId
                        }
                    }
                }

                bucket.startsWith("b:") -> {
                    val bucketId = bucket.removePrefix("b:")
                    if (bucketId.isNotBlank()) {
                        clauses += "$COLUMN_BUCKET_ID = ?"
                        args += bucketId
                    }
                }

                bucket.startsWith("name:") -> {
                    val name = bucket.removePrefix("name:")
                    if (name.isNotBlank()) {
                        clauses += "$COLUMN_BUCKET_DISPLAY_NAME = ?"
                        args += name
                        clauses += "$COLUMN_BUCKET_ID IS NULL"
                    }
                }
            }
        }

        val dateSeconds = beforeDateMs?.takeIf { it > 0 }?.div(1_000L)
        val id = beforeId?.toLongOrNull()?.takeIf { it > 0 }
        if (dateSeconds != null && id != null) {
            clauses += "($DATE_EXPRESSION < ? OR ($DATE_EXPRESSION = ? AND $COLUMN_ID < ?))"
            args += dateSeconds.toString()
            args += dateSeconds.toString()
            args += id.toString()
        }

        return PhotoSelection(clauses.joinToString(" AND "), args.toTypedArray())
    }
}
