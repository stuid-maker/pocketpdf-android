package com.asuka.pocketpdf.ui.components

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.asuka.pocketpdf.ui.theme.PocketPDFTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PocketComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactButtonExposesClickAction() {
        var clicked = false
        composeRule.setContent {
            PocketPDFTheme {
                PocketCompactButton("重新检测", onClick = { clicked = true })
            }
        }

        composeRule.onNodeWithText("重新检测")
            .assertHasClickAction()
            .performClick()
        assertTrue(clicked)
    }

    @Test
    fun emptyStateExposesTitleAndAction() {
        composeRule.setContent {
            PocketPDFTheme {
                PocketEmptyState(
                    title = "从一份文档开始",
                    message = "本地建立索引",
                    actionLabel = "导入 PDF",
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("从一份文档开始").assertIsDisplayed()
        composeRule.onNodeWithText("导入 PDF").assertHasClickAction()
    }
}
