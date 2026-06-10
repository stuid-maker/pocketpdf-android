package com.asuka.pocketpdf.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatFollowPolicyTest {

    @Test
    fun `new message follows even when viewport is away from bottom`() {
        assertTrue(
            shouldFollowLatest(
                messageCountChanged = true,
                isNearBottom = false,
                isStreaming = false,
            )
        )
    }

    @Test
    fun `streaming follows while viewport is near bottom`() {
        assertTrue(
            shouldFollowLatest(
                messageCountChanged = false,
                isNearBottom = true,
                isStreaming = true,
            )
        )
    }

    @Test
    fun `streaming does not override intentional upward scroll`() {
        assertFalse(
            shouldFollowLatest(
                messageCountChanged = false,
                isNearBottom = false,
                isStreaming = true,
            )
        )
    }
}
