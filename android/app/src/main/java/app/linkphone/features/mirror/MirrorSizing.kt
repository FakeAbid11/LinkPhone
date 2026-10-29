package app.linkphone.features.mirror

import kotlin.math.max
import kotlin.math.roundToInt

/** Pure size selection; the H.264 encoder path uses 1600 px long edge by default. */
object MirrorSizing {
    const val JPEG_MAX_EDGE = 1_280
    private val VIDEO_EDGES = intArrayOf(1_600, 1_440, 1_280, 960)

    fun jpegSize(width: Int, height: Int): Pair<Int, Int> =
        scaleToLongEdge(width, height, JPEG_MAX_EDGE).let { even(it.first) to even(it.second) }

    fun chooseVideoSize(
        width: Int,
        height: Int,
        widthAlignment: Int,
        heightAlignment: Int,
        onlyLongestEdge: Boolean = false,
        isSupported: (Int, Int) -> Boolean,
    ): Pair<Int, Int>? {
        val alignW = widthAlignment.coerceAtLeast(2)
        val alignH = heightAlignment.coerceAtLeast(2)
        val edges = if (onlyLongestEdge) intArrayOf(VIDEO_EDGES.first()) else VIDEO_EDGES
        for (edge in edges) {
            val scaled = scaleToLongEdge(width, height, edge)
            val candidateW = alignDown(scaled.first, alignW)
            val candidateH = alignDown(scaled.second, alignH)
            if (candidateW >= alignW && candidateH >= alignH && isSupported(candidateW, candidateH)) {
                return candidateW to candidateH
            }
        }
        return null
    }

    fun scaleToLongEdge(width: Int, height: Int, longEdge: Int): Pair<Int, Int> {
        val safeWidth = width.coerceAtLeast(2)
        val safeHeight = height.coerceAtLeast(2)
        val longest = max(safeWidth, safeHeight)
        if (longest <= longEdge) return safeWidth to safeHeight
        val ratio = longEdge.toDouble() / longest
        return (safeWidth * ratio).roundToInt().coerceAtLeast(2) to
            (safeHeight * ratio).roundToInt().coerceAtLeast(2)
    }

    fun alignDown(value: Int, alignment: Int): Int {
        val step = alignment.coerceAtLeast(2)
        return (value.coerceAtLeast(step) / step) * step
    }

    fun even(value: Int): Int = (value.coerceAtLeast(2) / 2) * 2
}
