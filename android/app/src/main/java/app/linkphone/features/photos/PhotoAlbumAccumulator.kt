package app.linkphone.features.photos

import app.linkphone.core.model.PhotoAlbumDto

class PhotoAlbumAccumulator {
    private data class Album(
        var count: Int,
        var coverId: Long,
        var latestSeconds: Long,
        var name: String,
    )

    private val albums = LinkedHashMap<String, Album>()

    fun add(albumId: String, name: String, photoId: Long, dateSeconds: Long) {
        if (photoId <= 0) return
        val existing = albums[albumId]
        if (existing == null) {
            albums[albumId] = Album(
                count = 1,
                coverId = photoId,
                latestSeconds = dateSeconds,
                name = name,
            )
            return
        }
        existing.count += 1
        if (dateSeconds > existing.latestSeconds ||
            (dateSeconds == existing.latestSeconds && photoId > existing.coverId)
        ) {
            existing.coverId = photoId
            existing.latestSeconds = dateSeconds
            existing.name = name
        }
    }

    fun result(): List<PhotoAlbumDto> = albums.entries
        .map { (id, album) ->
            PhotoAlbumDto(
                id = id,
                name = album.name,
                count = album.count,
                coverId = album.coverId.toString(),
                latest = album.latestSeconds * 1_000L,
            )
        }
        .sortedByDescending { it.latest }
}
