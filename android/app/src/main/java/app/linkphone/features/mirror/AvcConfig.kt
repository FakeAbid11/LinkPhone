package app.linkphone.features.mirror

import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Pure Annex B helpers shared by the encoder and JVM tests. MediaCodec emits H.264
 * access units with start codes; the PC decoder is configured for Annex B, so the
 * parameter sets travel in-band with every key frame.
 */
object AvcConfig {
    private const val NAL_SPS = 7
    private const val NAL_PPS = 8
    private val START_CODE = byteArrayOf(0, 0, 0, 1)

    fun splitAnnexB(data: ByteArray): List<ByteArray> {
        val starts = startCodes(data)
        if (starts.isEmpty()) return emptyList()
        val units = ArrayList<ByteArray>(starts.size)
        for (index in starts.indices) {
            val (start, length) = starts[index]
            val nalStart = start + length
            val nalEnd = if (index + 1 < starts.size) starts[index + 1].first else data.size
            if (nalEnd > nalStart) units += data.copyOfRange(nalStart, nalEnd)
        }
        return units
    }

    /** SPS + PPS in Annex B form, or null when the buffer has neither. */
    fun extractConfig(data: ByteArray): ByteArray? {
        val units = splitAnnexB(data)
        val selected = units.filter { nalType(it) == NAL_SPS || nalType(it) == NAL_PPS }
        return if (selected.isEmpty()) null else join(selected)
    }

    /**
     * Some encoders return codec-specific data as raw NAL units, some as Annex B.
     * Normalize both into SPS/PPS-only Annex B.
     */
    fun normalizeConfig(vararg buffers: ByteArray?): ByteArray? {
        val bytes = buffers.filterNotNull()
        if (bytes.isEmpty()) return null
        val combined = join(bytes)
        extractConfig(combined)?.let { return it }
        val units = bytes.filter { nalType(it) == NAL_SPS || nalType(it) == NAL_PPS }
        return if (units.isEmpty()) null else join(units)
    }

    /** Returns `avc1.PPCCLL` from an SPS NAL (with or without a start code). */
    fun codecString(data: ByteArray): String? {
        val sps = splitAnnexB(data).firstOrNull { nalType(it) == NAL_SPS }
            ?: data.takeIf { nalType(it) == NAL_SPS }
            ?: return null
        if (sps.size < 4) return null
        return String.format(
            Locale.US,
            "avc1.%02X%02X%02X",
            sps[1].toInt() and 0xFF,
            sps[2].toInt() and 0xFF,
            sps[3].toInt() and 0xFF,
        )
    }

    /** Ensures SPS/PPS are present before a key frame without duplicating them. */
    fun prependIfMissing(accessUnit: ByteArray, config: ByteArray?): ByteArray {
        if (config == null || config.isEmpty()) return accessUnit
        val units = splitAnnexB(accessUnit)
        if (units.any { nalType(it) == NAL_SPS }) return accessUnit
        return join(listOf(config, accessUnit))
    }

    fun nalType(nal: ByteArray): Int = if (nal.isEmpty()) -1 else nal[0].toInt() and 0x1F

    private fun join(parts: List<ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        for (part in parts) {
            val units = if (splitAnnexB(part).isEmpty()) listOf(part) else splitAnnexB(part)
            for (unit in units) {
                if (unit.isEmpty()) continue
                output.write(START_CODE)
                output.write(unit)
            }
        }
        return output.toByteArray()
    }

    private fun startCodes(data: ByteArray): List<Pair<Int, Int>> {
        val starts = ArrayList<Pair<Int, Int>>()
        var index = 0
        while (index + 2 < data.size) {
            if (data[index] == 0.toByte() &&
                data[index + 1] == 0.toByte() &&
                data[index + 2] == 1.toByte()
            ) {
                val start = if (index > 0 && data[index - 1] == 0.toByte()) index - 1 else index
                val length = index - start + 3
                starts += start to length
                index += 3
            } else {
                index++
            }
        }
        return starts
    }
}
