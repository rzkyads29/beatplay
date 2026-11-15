package com.utaloom.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * The payment screen encodes the QRIS payload locally, so the encoder has to produce a
 * real, scannable QR code (correct finder patterns) and a savable PNG.
 */
class QrCodeTest {

    // Realistic QRIS payload shape (EMVCo TLV, ASCII digits + uppercase).
    private val qris = "00020101021226610014ID.CO.QRIS.WWW01189360000900000000000215" +
        "936000090000000000303UMI5204541153033605405200015802ID5910TOKO ONLINE6007JAKARTA" +
        "61051234062070703A016304ABCD"

    @Test
    fun encodesPayloadIntoASquareMatrix() {
        val modules = qrModules(qris)
        assertNotNull(modules)
        modules!!
        assertTrue("QR version 1..40 => 21..177 modules", modules.size in 21..177)
        assertEquals("matrix must be square", modules.size, modules[0].size)
    }

    @Test
    fun drawsTheThreeFinderPatterns() {
        val modules = qrModules(qris)!!
        val n = modules.size
        for ((ox, oy) in listOf(0 to 0, 0 to n - 7, n - 7 to 0)) {
            val expected = listOf(
                "1111111",
                "1000001",
                "1011101",
                "1011101",
                "1011101",
                "1000001",
                "1111111",
            )
            for (y in 0 until 7) {
                for (x in 0 until 7) {
                    val dark = modules[oy + y][ox + x]
                    assertEquals(
                        "finder pattern mismatch at ($ox,$oy)+($x,$y)",
                        expected[y][x] == '1',
                        dark,
                    )
                }
            }
        }
    }

    @Test
    fun encodesDifferentPayloadsToDifferentCodes() {
        assertFalse(qrModules(qris)!!.contentDeepEquals(qrModules(qris.dropLast(1))!!))
    }

    @Test
    fun writesASavablePng() {
        val dir = File(System.getProperty("java.io.tmpdir"), "beatplay-qr-test-${System.nanoTime()}")
        val target = File(dir, "qris.png")
        assertTrue(writeQrPng(qris, target))
        assertTrue(target.isFile)

        val image = ImageIO.read(target)
        assertNotNull(image)
        val modules = qrModuleCount(qris)
        assertEquals((modules + 8) * 12, image.width)
        assertEquals(image.width, image.height)
        // Top-left corner is white (quiet zone), the finder pattern starts 4 modules in.
        assertEquals(0xFFFFFF, image.getRGB(2, 2) and 0xFFFFFF)

        dir.deleteRecursively()
    }

    @Test
    fun unusablePayloadFailsGracefully() {
        // Empty content cannot be encoded: callers fall back to the browser button.
        assertNull(qrModules(""))
        assertFalse(writeQrPng("", File(System.getProperty("java.io.tmpdir"), "beatplay-should-not-exist.png")))
    }
}
