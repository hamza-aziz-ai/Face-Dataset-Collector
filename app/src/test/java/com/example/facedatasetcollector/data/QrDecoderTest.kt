package com.example.facedatasetcollector.data

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrDecoderTest {
    private val payload = """{"base_url":"https://client.example.com","client_id":"client-a"}"""

    /** A synthetic frame: the code drawn on white, with [padding] extra bytes per row, and a last row without them as on real cameras. */
    private fun frame(size: Int, padding: Int): ByteArray {
        val code = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
        val stride = size + padding
        val luma = ByteArray(stride * (size - 1) + size) { 0x55 }
        for (y in 0 until size) {
            for (x in 0 until size) luma[y * stride + x] = if (code[x, y]) 0 else -1
        }
        return luma
    }

    @Test
    fun readsACode() {
        assertEquals(payload, decodeQr(frame(480, 0), 480, 480, 480))
    }

    @Test
    fun readsACodeWhenRowsArePadded() {
        assertEquals(payload, decodeQr(frame(480, 64), 544, 480, 480))
    }

    @Test
    fun aFrameWithNoCodeGivesNull() {
        assertNull(decodeQr(ByteArray(640 * 480) { (it % 251).toByte() }, 640, 640, 480))
    }
}
