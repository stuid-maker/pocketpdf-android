package com.asuka.pocketpdf.ui.library

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.asuka.pocketpdf.ui.theme.PocketPDFTheme
import org.junit.Rule
import org.junit.Test

class LibraryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyLibraryShowsBrandMessageAndImportAction() {
        composeRule.setContent {
            PocketPDFTheme {
                LibraryScreen(
                    state = LibraryUiState.Empty,
                    onImport = {},
                    onOpenDocument = {},
                    onOpenSettings = {},
                    onRetryIndexing = {},
                    onDeleteDocument = {},
                    coverLoader = null,
                )
            }
        }

        composeRule.onNodeWithText("从一份文档开始").assertIsDisplayed()
        composeRule.onNodeWithText("导入 PDF").assertHasClickAction()
    }
}
