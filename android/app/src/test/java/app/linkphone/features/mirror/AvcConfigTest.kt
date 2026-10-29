package app.linkphone.features.mirror

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvcConfigTest {
    private val sps = byteArrayOf(0, 0, 0, 1, 0x67, 0x42, 0x00, 0x1F, 0x10)
    private val pps = byteArrayOf(0, 0, 0, 1, 0x68, 0xCE.toByte(), 0x3C, 0x80.toByte())
    private val idr = byteArrayOf(0, 0, 0, 1, 0x65, 0x88.toByte(), 0x84.toByte(), 0x00)

    @Test
    fun splitsThreeAndFourByteStartCodes() {
        val data = byteArrayOf(
            0, 0, 0, 1, 0x67, 0x42,
            0, 0, 1, 0x68, 0xCE.toByte(),
            0, 0, 0, 1, 0x65, 0x88.toByte(),
        )
        val units = AvcConfig.splitAnnexB(data)
        assertEquals(3, units.size)
        assertEquals(0x67, units[0][0].toInt())
        assertEquals(0x68, units[1][0].toInt())
        assertEquals(0x65, units[2][0].toInt())
    }

    @Test
    fun derivesTheWebCodecsCodecString() {
        assertEquals("avc1.42001F", AvcConfig.codecString(sps + pps))
        assertEquals("avc1.640028", AvcConfig.codecString(byteArrayOf(0x67, 0x64, 0x00, 0x28)))
        assertNull(AvcConfig.codecString(pps))
    }

    @Test
    fun extractsOnlySpsAndPps() {
        val config = AvcConfig.extractConfig(sps + idr + pps)
        assertEquals(listOf(0x67, 0x68), AvcConfig.splitAnnexB(config!!).map { it[0].toInt() })
    }

    @Test
    fun normalizesRawCodecSpecificData() {
        val rawSps = byteArrayOf(0x67, 0x42, 0x00, 0x1F, 0x10)
        val rawPps = byteArrayOf(0x68, 0xCE.toByte(), 0x3C, 0x80.toByte())
        val config = AvcConfig.normalizeConfig(rawSps, rawPps)
        assertEquals(listOf(0x67, 0x68), AvcConfig.splitAnnexB(config!!).map { it[0].toInt() })
    }

    @Test
    fun prependsConfigToKeyFramesWithoutDuplicatingIt() {
        val config = AvcConfig.extractConfig(sps + pps)
        val withConfig = AvcConfig.prependIfMissing(idr, config)
        assertEquals(listOf(0x67, 0x68, 0x65), AvcConfig.splitAnnexB(withConfig).map { it[0].toInt() })

        val alreadyConfigured = sps + idr
        assertArrayEquals(alreadyConfigured, AvcConfig.prependIfMissing(alreadyConfigured, config))
        assertTrue(AvcConfig.splitAnnexB(alreadyConfigured).any { it[0].toInt() == 0x67 })
    }
}
