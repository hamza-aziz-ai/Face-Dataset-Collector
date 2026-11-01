package com.example.facedatasetcollector.data

import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * Finds a QR code in one brightness plane, or returns null. The detector works at any rotation, so the frame needs no turning.
 *
 * @param rowStride bytes per row in [luma], which can exceed [width] on real cameras.
 */
fun decodeQr(luma: ByteArray, rowStride: Int, width: Int, height: Int): String? {
    val source = PlanarYUVLuminanceSource(luma, rowStride, height, 0, 0, width, height, false)
    return try {
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: ReaderException) {
        null
    }
}
