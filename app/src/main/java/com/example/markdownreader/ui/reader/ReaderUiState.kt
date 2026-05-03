package com.example.markdownreader.ui.reader

import com.example.markdownreader.data.MarkdownDocument
import com.example.markdownreader.data.ReadError

const val DEFAULT_SCROLL_OFFSET = 0

enum class ReadingMode(val storageValue: String) {
    Scroll("scroll"),
    Paged("paged");

    companion object {
        fun fromStorageValue(value: String): ReadingMode {
            return entries.firstOrNull { it.storageValue == value } ?: Scroll
        }
    }
}

enum class ReaderRoute {
    Bookshelf,
    Reader
}

data class StoredDocument(
    val uri: String,
    val fileName: String,
    val importedAt: Long,
    val lastOpenedAt: Long
)

data class ScrollProgress(
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = DEFAULT_SCROLL_OFFSET
)

data class ReaderUiState(
    val route: ReaderRoute = ReaderRoute.Bookshelf,
    val isLoading: Boolean = false,
    val bookshelf: List<StoredDocument> = emptyList(),
    val currentDocumentUri: String? = null,
    val document: MarkdownDocument? = null,
    val readingMode: ReadingMode = ReadingMode.Scroll,
    val currentPage: Int = 0,
    val currentScrollProgress: ScrollProgress = ScrollProgress(),
    val error: ReadError? = null,
    val isRestoredFromHistory: Boolean = false
)
