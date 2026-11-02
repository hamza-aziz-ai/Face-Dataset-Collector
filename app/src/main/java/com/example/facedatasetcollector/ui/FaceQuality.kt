package com.example.facedatasetcollector.ui

import com.example.facedatasetcollector.data.Pose
import java.nio.ByteBuffer
import kotlin.math.abs

/** Plain box with right and bottom exclusive. Not android.graphics.Rect, so this file can run in JVM unit tests. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Halves of [set] whose photos look wrong for glasses, as their withGlasses value. Most photos in a half must agree, so one odd frame cannot raise it (D-021). A photo with no score does not count against its half.
 */
fun glassesMismatches(set: List<Pose>, bridge: Map<Pose, Float>): List<Boolean> =
    set.groupBy { it.withGlasses }
        .filter { (glasses, poses) -> poses.count { pose -> bridge[pose]?.let { (it > QualityPolicy.GLASSES_BRIDGE_EDGE) != glasses } == true } >= QualityPolicy.GLASSES_VOTES }
        .keys.toList()

/**
 * Converts a box from ML Kit coordinates to camera buffer coordinates. ML Kit reports faces in the upright image, but the buffer stays in sensor orientation, so reading it without this samples the wrong pixels.
 *
 * @param rotationDegrees clockwise rotation that makes the buffer upright, from ImageInfo.rotationDegrees.
 * @param bufferWidth width of the unrotated buffer.
 * @param bufferHeight height of the unrotated buffer.
 */
fun uprightToBuffer(box: Box, rotationDegrees: Int, bufferWidth: Int, bufferHeight: Int): Box =
    when (rotationDegrees) {
        0 -> box
        90 -> Box(box.top, bufferHeight - box.right, box.bottom, bufferHeight - box.left)
        180 -> Box(bufferWidth - box.right, bufferHeight - box.bottom, bufferWidth - box.left, bufferHeight - box.top)
        270 -> Box(bufferWidth - box.bottom, box.left, bufferWidth - box.top, box.right)
        else -> throw IllegalArgumentException("Unsupported rotation: $rotationDegrees")
    }

/**
 * Mean brightness step between neighbouring pixels inside [box]. Measured across the nose bridge, where a glasses frame adds sharp edges to smooth skin. It feeds a warning only, never a block (D-021).
 *
 * @return null when the box has too few pixels inside the buffer to measure.
 */
fun bridgeEdgeScore(buffer: ByteBuffer, rowStride: Int, pixelStride: Int, bufferWidth: Int, bufferHeight: Int, box: Box): Float? {
    val left = box.left.coerceAtLeast(0)
    val top = box.top.coerceAtLeast(0)
    val right = box.right.coerceAtMost(bufferWidth - 1)
    val bottom = box.bottom.coerceAtMost(bufferHeight - 1)
    if (right - left < 2 || bottom - top < 2) return null

    fun luma(x: Int, y: Int) = buffer.get(y * rowStride + x * pixelStride).toInt() and 0xFF
    var sum = 0L
    for (y in top until bottom) {
        for (x in left until right) {
            sum += abs(luma(x, y) - luma(x + 1, y)) + abs(luma(x, y) - luma(x, y + 1))
        }
    }
    return sum.toFloat() / ((right - left) * (bottom - top) * 2)
}

/**
 * Average luma inside [box], or null when the box lies fully outside the buffer. Assumes [buffer] is the Y plane of a YUV frame. Uses absolute reads, so the buffer position is unchanged.
 */
fun averageLuma(
    buffer: ByteBuffer,
    rowStride: Int,
    pixelStride: Int,
    bufferWidth: Int,
    bufferHeight: Int,
    box: Box,
): Int? {
    // Face boxes can extend past the frame edge.
    val left = box.left.coerceAtLeast(0)
    val top = box.top.coerceAtLeast(0)
    val right = box.right.coerceAtMost(bufferWidth)
    val bottom = box.bottom.coerceAtMost(bufferHeight)
    if (left >= right || top >= bottom) return null

    var sum = 0L
    for (y in top until bottom) {
        for (x in left until right) {
            sum += buffer.get(y * rowStride + x * pixelStride).toInt() and 0xFF
        }
    }
    return (sum / ((right - left).toLong() * (bottom - top))).toInt()
}
