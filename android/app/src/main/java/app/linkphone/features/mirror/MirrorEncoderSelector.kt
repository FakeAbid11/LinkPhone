package app.linkphone.features.mirror

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat

data class EncoderChoice(
    val name: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val videoCapabilities: MediaCodecInfo.VideoCapabilities,
)

/** Finds a hardware AVC surface encoder and builds a 30/60 fps format. */
object MirrorEncoderSelector {
    const val DEFAULT_FPS = 30
    const val HIGH_FPS = 60
    const val I_FRAME_SECONDS = 1

    private const val BITRATE_30 = 6_000_000
    private const val BITRATE_60 = 12_000_000

    fun avcEncoderAvailable(): Boolean = candidates().isNotEmpty()

    fun choose(screenWidth: Int, screenHeight: Int, requestedFps: Int): EncoderChoice? {
        val target = if (requestedFps >= HIGH_FPS) HIGH_FPS else DEFAULT_FPS
        if (target == HIGH_FPS) {
            chooseAt(screenWidth, screenHeight, HIGH_FPS, onlyLongestEdge = true)
                ?.let { return it }
        }
        return chooseAt(screenWidth, screenHeight, DEFAULT_FPS, onlyLongestEdge = false)
    }

    fun formatFor(choice: EncoderChoice, constantBitrate: Boolean): MediaFormat {
        val bitrate = bitrateFor(choice.fps).coerceIn(
            choice.videoCapabilities.bitrateRange.lower.toInt(),
            choice.videoCapabilities.bitrateRange.upper.toInt(),
        )
        return MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, choice.width, choice.height)
            .apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                )
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, choice.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_SECONDS)
                setInteger(
                    MediaFormat.KEY_BITRATE_MODE,
                    if (constantBitrate) {
                        MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                    } else {
                        MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
                    },
                )
                setInteger(MediaFormat.KEY_PRIORITY, 0)
                setInteger(MediaFormat.KEY_OPERATING_RATE, choice.fps)
            }
    }

    private fun chooseAt(
        screenWidth: Int,
        screenHeight: Int,
        fps: Int,
        onlyLongestEdge: Boolean,
    ): EncoderChoice? {
        for (info in candidates()) {
            val capabilities = runCatching {
                info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
            }.getOrNull() ?: continue
            if (!capabilities.colorFormats.contains(
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                )
            ) {
                continue
            }
            val video = capabilities.videoCapabilities ?: continue
            val size = MirrorSizing.chooseVideoSize(
                width = screenWidth,
                height = screenHeight,
                widthAlignment = video.widthAlignment,
                heightAlignment = video.heightAlignment,
                onlyLongestEdge = onlyLongestEdge,
            ) { width, height ->
                runCatching {
                    video.areSizeAndRateSupported(width, height, fps.toDouble())
                }.getOrDefault(false)
            } ?: continue
            return EncoderChoice(info.name, size.first, size.second, fps, video)
        }
        return null
    }

    private fun bitrateFor(fps: Int): Int =
        if (fps >= HIGH_FPS) BITRATE_60 else BITRATE_30

    private fun candidates(): List<MediaCodecInfo> = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
            info.isEncoder &&
                info.isHardwareAccelerated &&
                info.supportedTypes.any {
                    it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)
                } &&
                runCatching {
                    info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                        .colorFormats
                        .contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                }.getOrDefault(false)
        }
    }.getOrDefault(emptyList())
}
