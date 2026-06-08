package com.asuka.pocketpdf.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

class PocketTokensTest {

    @Test
    fun motionDurationsStayRestrained() {
        assertTrue(PocketMotion.Micro in 100..180)
        assertTrue(PocketMotion.Content in 180..220)
        assertTrue(PocketMotion.Panel in 200..240)
    }
}
