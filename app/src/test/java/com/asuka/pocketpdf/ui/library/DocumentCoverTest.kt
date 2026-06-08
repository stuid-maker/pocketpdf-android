package com.asuka.pocketpdf.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentCoverTest {

    @Test
    fun sameDocumentGetsSameFallback() {
        assertEquals(
            fallbackCover(42L, "Effective Kotlin"),
            fallbackCover(42L, "Effective Kotlin"),
        )
    }

    @Test
    fun fallbackUsesFirstVisibleCharacterAndKnownPalette() {
        val cover = fallbackCover(42L, "  Effective Kotlin  ")

        assertEquals("E", cover.label)
        assertTrue(cover.paletteIndex in 0..3)
    }
}
