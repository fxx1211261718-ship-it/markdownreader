package com.example.markdownreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.ClickableText
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.ThematicBreak
import org.commonmark.node.Text as MarkdownText

private const val LINK_TAG = "markdown_link"

@Composable
fun MarkdownBlockContent(
    blocks: List<Node>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is Heading -> HeadingBlock(block)
                is Paragraph -> ParagraphBlock(block)
                is FencedCodeBlock -> {
                    if (block.info.orEmpty().trim().equals("math", ignoreCase = true)) {
                        MathBlock(block.literal.orEmpty())
                    } else {
                        CodeBlock(block.literal.orEmpty(), block.info.orEmpty().trim())
                    }
                }
                is BulletList -> ListBlock(block, ordered = false)
                is OrderedList -> ListBlock(block, ordered = true)
                is BlockQuote -> QuoteBlock(block)
                is ThematicBreak -> ThematicBreakBlock()
            }
        }
    }
}

internal fun parseMarkdownBlocks(content: String): List<Node> {
    val parser = org.commonmark.parser.Parser.builder().build()
    val document = parser.parse(preprocessMarkdown(content))
    return document.children().toList().ifEmpty { listOf(document) }
}

private fun Node.children(): Sequence<Node> = sequence {
    var child = firstChild
    while (child != null) {
        yield(child)
        child = child.next
    }
}

@Composable
private fun HeadingBlock(heading: Heading) {
    val style = when (heading.level) {
        1 -> MaterialTheme.typography.headlineMedium
        2 -> MaterialTheme.typography.headlineSmall
        3 -> MaterialTheme.typography.titleLarge
        4 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    Text(
        text = collectInlineText(heading).trim(),
        style = style,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun ParagraphBlock(paragraph: Paragraph) {
    ClickableAnnotatedText(
        text = collectInlineAnnotatedText(paragraph),
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun CodeBlock(code: String, language: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 8.dp)
    ) {
        if (language.isNotEmpty()) {
            Text(
                text = language,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = code.trimEnd(),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun MathBlock(expression: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = expression.trimEnd(),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun ListBlock(list: Node, ordered: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.children().forEachIndexed { index, item ->
            if (item is ListItem) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (ordered) "${index + 1}." else "•",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    ClickableAnnotatedText(
                        text = collectInlineAnnotatedText(item),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuoteBlock(blockQuote: BlockQuote) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        ClickableAnnotatedText(
            text = collectInlineAnnotatedText(blockQuote),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThematicBreakBlock() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun ClickableAnnotatedText(
    text: AnnotatedString,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val uriHandler = LocalUriHandler.current
    ClickableText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color),
        onClick = { offset ->
            text.getStringAnnotations(LINK_TAG, offset, offset)
                .firstOrNull()
                ?.let { uriHandler.openUri(it.item) }
        }
    )
}

private fun collectInlineText(node: Node): String {
    return collectInlineAnnotatedText(node).text.replace("\n\n\n", "\n\n")
}

private fun collectInlineAnnotatedText(node: Node): AnnotatedString {
    return buildAnnotatedString {
        appendInline(node)
    }
}

private fun AnnotatedString.Builder.appendInline(node: Node) {
    when (node) {
        is MarkdownText -> append(node.literal)
        is SoftLineBreak, is HardLineBreak -> append('\n')
        is Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(node.literal) }
        is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            node.children().forEach { appendInline(it) }
        }
        is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            node.children().forEach { appendInline(it) }
        }
        is Link -> {
            val start = length
            pushStringAnnotation(LINK_TAG, node.destination)
            withStyle(
                SpanStyle(
                    color = Color(0xFF1565C0),
                    textDecoration = TextDecoration.Underline
                )
            ) {
                val labelBefore = length
                node.children().forEach { appendInline(it) }
                if (length == labelBefore) {
                    append(node.destination)
                }
            }
            pop()
            if (length == start) {
                append(node.destination)
            }
        }
        is Paragraph, is Heading, is ListItem, is Document, is BlockQuote -> {
            node.children().forEach { appendInline(it) }
        }
        else -> node.children().forEach { appendInline(it) }
    }
}
