package com.example.markdownreader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class ReaderScreenSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bookshelfShowsImportButton() {
        composeRule.onNodeWithText("导入文件").assertIsDisplayed()
        composeRule.onNodeWithText("书架还是空的").assertIsDisplayed()
    }
}
