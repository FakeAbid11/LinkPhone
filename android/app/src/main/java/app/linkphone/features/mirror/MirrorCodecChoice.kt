package app.linkphone.features.mirror

/** Resolves the PC's requested mirror codec into a codec the phone can actually run. */
object MirrorCodecChoice {
    const val H264 = "h264"
    const val JPEG = "jpeg"
    const val FPS_30 = 30
    const val FPS_60 = 60

    fun normalize(requested: String?): String =
        if (requested?.trim()?.lowercase() == H264) H264 else JPEG

    fun resolve(requested: String?, h264Available: Boolean): String =
        if (normalize(requested) == H264 && h264Available) H264 else JPEG

    fun normalizeFps(requested: Int?): Int =
        if (requested == FPS_60) FPS_60 else FPS_30
}
