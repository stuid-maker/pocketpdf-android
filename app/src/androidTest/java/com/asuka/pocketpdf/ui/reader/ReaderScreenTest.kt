package com.asuka.pocketpdf.ui.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.asuka.pocketpdf.ui.ai.GenerationProgressDisplay
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

    @Test
    fun collapseKeepsProgressVisibleAndAllowsReopen() {
        var stopCount = 0
        composeRule.setContent {
            PocketPDFTheme {
                ReaderScreen(
                    title = "paper.pdf",
                    pageState = ReaderPageState(pageIndex = 2, pageCount = 8),
                    summaryState = SummaryState.Generating(
                        progress = GenerationProgressDisplay(
                            fraction = .4f,
                            stageLabel = "正在总结第 3 / 8 部分",
                            remainingSeconds = 80,
                        ),
                    ),
                    isIndexed = true,
                    onBack = {},
                    onPageRequested = {},
                    onSummarizePage = {},
                    onSummarizeDocument = {},
                    onStopSummary = { stopCount++ },
                    onOpenChat = {},
                )
            }
        }

        composeRule.onNodeWithText("收起并继续阅读").performClick()
        assertEquals(0, stopCount)
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onNode(
            hasContentDescription("打开 AI 总结") and
                hasText("正在总结第 3 / 8 部分", substring = true) and
                hasText("约剩 1分20秒"),
        ).assertExists()

        composeRule.onNodeWithContentDescription("打开 AI 总结").performClick()
        composeRule.onNodeWithText("停止生成").performClick()
        assertEquals(1, stopCount)
    }
}
