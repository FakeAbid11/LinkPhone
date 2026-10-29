package app.linkphone.features.mirror

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat

data class EncoderChoice(
    val name: String,
    val width: Int,
    val height: Int,
    val videoCapabilities: MediaCodecInfo.VideoCapabilities,
)

/** Finds a hardware AVC surface encoder and builds a 30 fps, 6 Mbps format. */
object MirrorEncoderSelector {
    const val FRAME_RATE = 30
    const val BITRATE = 6_000_000
    const val I_FRAME_SECONDS = 1

    fun avcEncoderAvailable(): Boolean = candidates().isNotEmpty()

    fun choose(screenWidth: Int, screenHeight: Int): EncoderChoice? {
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
            ) { width, height -> video.areSizeAndRateSupported(width, height, FRAME_RATE.toDouble()) }
                ?: continue
            return EncoderChoice(info.name, size.first, size.second, video)
        }
        return null
    }

    fun formatFor(choice: EncoderChoice, constantBitrate: Boolean): MediaFormat {
        val bitrate = BITRATE.coerceIn(
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
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
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
                setInteger(MediaFormat.KEY_OPERATING_RATE, FRAME_RATE)
            }
    }

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
