package com.example.markdownreader.ui.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.markdownreader.R

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PagedMarkdownReader(
    content: String,
    currentPage: Int,
    onPageChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pages = remember(content) { paginateMarkdown(content) }
    val pageCount = pages.size.coerceAtLeast(1)
    val safeInitialPage = currentPage.coerceIn(0, pageCount - 1)
    val pagerState = rememberPagerState(initialPage = safeInitialPage) {
        pageCount
    }

    LaunchedEffect(pagerState.currentPage, pageCount) {
        onPageChanged(pagerState.currentPage, pageCount)
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 32.dp)
        ) { page ->
            MarkdownBlockContent(
                blocks = pages[page],
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
        Text(
            text = stringResource(R.string.reader_page_indicator, pagerState.currentPage + 1, pages.size),
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .fillMaxWidth(),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

internal fun paginateMarkdown(content: String, targetCharsPerPage: Int = 2200): List<List<org.commonmark.node.Node>> {
    val blocks = parseMarkdownBlocks(content)
    if (blocks.isEmpty()) {
        return listOf(emptyList())
    }

    val pages = mutableListOf<MutableList<org.commonmark.node.Node>>()
    var currentPage = mutableListOf<org.commonmark.node.Node>()
    var currentSize = 0

    for (block in blocks) {
        val blockText = block.literalText()
        val blockSize = blockText.length.coerceAtLeast(1)
        if (currentPage.isNotEmpty() && currentSize + blockSize > targetCharsPerPage) {
            pages += currentPage
            currentPage = mutableListOf()
            currentSize = 0
        }
        currentPage += block
        currentSize += blockSize
    }

    if (currentPage.isNotEmpty()) {
        pages += currentPage
    }

    return pages.ifEmpty { listOf(emptyList()) }
}

private fun org.commonmark.node.Node.literalText(): String {
    return buildString {
        appendNode(this@literalText)
    }
}

private fun StringBuilder.appendNode(node: org.commonmark.node.Node) {
    val literal = when (node) {
        is org.commonmark.node.Text -> node.literal
        is org.commonmark.node.Code -> node.literal
        is org.commonmark.node.FencedCodeBlock -> node.literal.orEmpty()
        else -> ""
    }
    append(literal)
    var child = node.firstChild
    while (child != null) {
        appendNode(child)
        child = child.next
    }
    if (node is org.commonmark.node.Paragraph || node is org.commonmark.node.Heading) {
        append('\n')
    }
}
