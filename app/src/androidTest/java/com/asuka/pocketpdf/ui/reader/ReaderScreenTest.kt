package com.asuka.pocketpdf.ui.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.asuka.pocketpdf.ui.theme.PocketPDFTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReaderScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readerChromeExposesPageNavigationAndAi() {
        var requestedPage = -1
        composeRule.setContent {
            PocketPDFTheme {
                ReaderScreen(
                    title = "paper.pdf",
                    pageState = ReaderPageState(pageIndex = 19, pageCount = 29),
                    summaryState = SummaryState.Idle,
                    isIndexed = true,
                    onBack = {},
                    onPageRequested = { requestedPage = it },
                    onSummarizePage = {},
                    onSummarizeDocument = {},
                    onStopSummary = {},
                    onOpenChat = {},
                )
            }
        }

        composeRule.onNodeWithText("20 / 29").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("上一页").performClick()
        assertEquals(18, requestedPage)
        composeRule.onNodeWithContentDescription("下一页").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("页面摘要").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("文档 AI").assertIsDisplayed()
    }
}
