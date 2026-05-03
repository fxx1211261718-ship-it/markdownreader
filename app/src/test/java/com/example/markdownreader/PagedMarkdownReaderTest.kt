package com.example.markdownreader

import com.example.markdownreader.ui.reader.paginateMarkdown
import com.example.markdownreader.ui.reader.preprocessMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedMarkdownReaderTest {

    @Test
    fun splitsLongMarkdownIntoMultiplePages() {
        val content = buildString {
            repeat(20) { index ->
                append("## Section $index\n")
                append("这是一段用于分页测试的内容。".repeat(20))
                append("\n\n")
            }
        }

        val pages = paginateMarkdown(content, targetCharsPerPage = 300)

        assertTrue(pages.size > 1)
    }

    @Test
    fun keepsSingleShortMarkdownOnOnePage() {
        val content = "# Title\nShort paragraph"

        val pages = paginateMarkdown(content, targetCharsPerPage = 300)

        assertEquals(1, pages.size)
    }

    @Test
    fun preprocessesDisplayMathBlockIntoMathFence() {
        val content = "before\n$$\na^2+b^2=c^2\n$$\nafter"

        val processed = preprocessMarkdown(content)

        assertTrue(processed.contains("```math"))
        assertTrue(processed.contains("a^2+b^2=c^2"))
        assertTrue(processed.contains("```"))
    }
}
