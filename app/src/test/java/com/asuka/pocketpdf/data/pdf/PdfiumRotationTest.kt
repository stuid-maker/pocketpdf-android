package com.asuka.pocketpdf.data.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfiumRotationTest {

    @Test
    fun `converts pdfium quarter turns to clockwise degrees`() {
        assertEquals(0, pdfiumRotationToDegrees(0))
        assertEquals(90, pdfiumRotationToDegrees(1))
        assertEquals(180, pdfiumRotationToDegrees(2))
        assertEquals(270, pdfiumRotationToDegrees(3))
    }

    @Test
    fun `rejects invalid pdfium rotation`() {
        assertThrows(IllegalArgumentException::class.java) {
            pdfiumRotationToDegrees(4)
        }
    }
}
