package com.example.facedatasetcollector.ui

import com.example.facedatasetcollector.data.Pose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer

/** All pixels here are synthetic. Real photos of people are never allowed in tests. */
class FaceQualityTest {

    /** Builds a Y plane whose pixels come from [pixel], with row padding and pixel gaps that must never be read. */
    private fun plane(width: Int, height: Int, rowStride: Int, pixelStride: Int, pixel: (Int, Int) -> Int): ByteBuffer {
        val bytes = ByteArray(rowStride * height) { PADDING }
        for (y in 0 until height) {
            for (x in 0 until width) {
                bytes[y * rowStride + x * pixelStride] = pixel(x, y).toByte()
            }
        }
        return ByteBuffer.wrap(bytes)
    }

    @Test
    fun uprightToBuffer_mapsEachRotationToTheSamePixels() {
        val width = 8
        val height = 6
        val inBuffer = Box(1, 2, 4, 3)

        // Upright boxes produced by rotating the buffer box clockwise by hand. 90 and 270 swap the frame dimensions.
        assertEquals(inBuffer, uprightToBuffer(Box(1, 2, 4, 3), 0, width, height))
        assertEquals(inBuffer, uprightToBuffer(Box(3, 1, 4, 4), 90, width, height))
        assertEquals(inBuffer, uprightToBuffer(Box(4, 3, 7, 4), 180, width, height))
        assertEquals(inBuffer, uprightToBuffer(Box(2, 4, 3, 7), 270, width, height))
    }

    /** Independent of the formulas: rotates real pixels clockwise, finds the bright box, then maps it back. */
    @Test
    fun uprightToBuffer_findsTheSamePixelsAfterRotatingTheImage() {
        val width = 8
        val height = 6
        val bright = setOf(1 to 2, 2 to 2, 3 to 2)

        for (rotation in listOf(0, 90, 180, 270)) {
            val upright = bright.map { (x, y) ->
                when (rotation) {
                    0 -> x to y
                    90 -> height - 1 - y to x
                    180 -> width - 1 - x to height - 1 - y
                    else -> y to width - 1 - x
                }
            }
            val box = Box(upright.minOf { it.first }, upright.minOf { it.second }, upright.maxOf { it.first } + 1, upright.maxOf { it.second } + 1)

            assertEquals("rotation $rotation", Box(1, 2, 4, 3), uprightToBuffer(box, rotation, width, height))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun uprightToBuffer_rejectsUnknownRotation() {
        uprightToBuffer(Box(0, 0, 1, 1), 45, 8, 6)
    }

    @Test
    fun averageLuma_readsOnlyTheBoxAndSkipsPadding() {
        val inBox = Box(2, 1, 5, 3)
        val buffer = plane(8, 6, rowStride = 19, pixelStride = 2) { x, y ->
            if (x in 2..4 && y in 1..2) 200 else 10
        }

        assertEquals(200, averageLuma(buffer, 19, 2, 8, 6, inBox))
    }

    @Test
    fun averageLuma_treatsBytesAsUnsigned() {
        val buffer = plane(4, 4, rowStride = 4, pixelStride = 1) { _, _ -> 240 }

        assertEquals(240, averageLuma(buffer, 4, 1, 4, 4, Box(0, 0, 4, 4)))
    }

    @Test
    fun averageLuma_clampsBoxToFrame() {
        val buffer = plane(4, 4, rowStride = 4, pixelStride = 1) { _, _ -> 120 }

        assertEquals(120, averageLuma(buffer, 4, 1, 4, 4, Box(-3, -3, 9, 9)))
    }

    @Test
    fun averageLuma_returnsNullWhenBoxIsOutsideFrame() {
        val buffer = plane(4, 4, rowStride = 4, pixelStride = 1) { _, _ -> 120 }

        assertNull(averageLuma(buffer, 4, 1, 4, 4, Box(10, 10, 20, 20)))
    }

    @Test
    fun averageLuma_leavesBufferPositionUnchanged() {
        val buffer = plane(4, 4, rowStride = 4, pixelStride = 1) { _, _ -> 120 }

        averageLuma(buffer, 4, 1, 4, 4, Box(0, 0, 4, 4))

        assertEquals(0, buffer.position())
    }

    private companion object {
        const val PADDING: Byte = 0x7F
    }
    private val plain = Pose.entries.filter { !it.withGlasses }
    private val glasses = Pose.entries.filter { it.withGlasses }
    private fun scores(poses: List<Pose>, vararg values: Float) = poses.zip(values.toList()).toMap()

    @Test
    fun theMeasuredSetsRaiseNoWarning() {
        // The owner's real runs, in pose order: phone, then tablet.
        val phone = scores(plain, 1.7f, 1.9f, 1.8f, 1.6f, 1.6f) + scores(glasses, 4.4f, 3.0f, 4.0f, 3.7f, 3.3f)
        val tablet = scores(plain, 1.7f, 1.5f, 2.1f, 1.8f, 1.8f) + scores(glasses, 3.5f, 2.3f, 3.7f, 3.3f, 3.1f)
        assertEquals(emptyList<Boolean>(), glassesMismatches(Pose.entries, phone))
        assertEquals(emptyList<Boolean>(), glassesMismatches(Pose.entries, tablet))
    }

    @Test
    fun mostOfAHalfMustDisagree() {
        val noGlassesOn = scores(glasses, 1.6f, 1.8f, 1.7f, 3.5f, 3.5f)
        assertEquals(listOf(true), glassesMismatches(Pose.entries, scores(plain, 1.7f, 1.7f, 1.7f, 1.7f, 1.7f) + noGlassesOn))
        val twoOdd = scores(plain, 3.0f, 3.0f, 1.7f, 1.7f, 1.7f)
        assertEquals(emptyList<Boolean>(), glassesMismatches(plain, twoOdd))
        val swapped = scores(plain, 3.5f, 3.5f, 3.5f, 1.7f, 1.7f) + scores(glasses, 1.7f, 1.7f, 1.7f, 3.5f, 3.5f)
        assertEquals(setOf(true, false), glassesMismatches(Pose.entries, swapped).toSet())
    }

    @Test
    fun missingScoresNeverCountAgainstAHalf() {
        assertEquals(emptyList<Boolean>(), glassesMismatches(glasses, scores(glasses, 1.5f, 1.5f)))
    }
}
