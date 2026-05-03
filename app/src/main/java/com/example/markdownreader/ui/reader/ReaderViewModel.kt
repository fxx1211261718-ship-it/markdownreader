package com.example.markdownreader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.markdownreader.data.DocumentLoadResult
import com.example.markdownreader.data.MarkdownDocument
import com.example.markdownreader.data.MarkdownDocumentDataSource
import com.example.markdownreader.data.MarkdownDocumentRepository
import com.example.markdownreader.data.ReaderPreferences
import com.example.markdownreader.data.ReaderPreferencesState
import com.example.markdownreader.data.ReaderPreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReaderViewModel(
    private val repository: MarkdownDocumentDataSource,
    private val preferences: ReaderPreferencesStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var latestPreferencesState = ReaderPreferencesState(
        readingMode = ReadingMode.Scroll,
        lastOpenedUri = null
    )

    init {
        observePreferences()
    }

    fun importDocument(uriString: String) {
        loadDocument(uriString, restored = false)
    }

    fun openDocument(uriString: String) {
        loadDocument(uriString, restored = false)
    }

    fun showBookshelf() {
        persistCurrentProgress()
        _uiState.update {
            it.copy(route = ReaderRoute.Bookshelf, isRestoredFromHistory = false)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun setReadingMode(mode: ReadingMode) {
        _uiState.update {
            it.copy(readingMode = mode)
        }
        viewModelScope.launch {
            preferences.saveReadingMode(mode)
        }
    }

    fun updateCurrentPage(page: Int, pageCount: Int) {
        val currentUri = _uiState.value.currentDocumentUri ?: return
        val safePage = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        _uiState.update { current ->
            if (current.readingMode != ReadingMode.Paged) current else current.copy(currentPage = safePage)
        }
        persistPagedPosition(currentUri, safePage)
    }

    fun updateScrollProgress(progress: ScrollProgress) {
        val currentUri = _uiState.value.currentDocumentUri ?: return
        _uiState.update { it.copy(currentScrollProgress = progress) }
        persistScrollPosition(currentUri, progress)
    }

    fun persistCurrentProgress() {
        val state = _uiState.value
        val currentUri = state.currentDocumentUri ?: return
        when (state.readingMode) {
            ReadingMode.Scroll -> persistScrollPosition(currentUri, state.currentScrollProgress)
            ReadingMode.Paged -> persistPagedPosition(currentUri, state.currentPage)
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferences.preferencesFlow.collect { state ->
                latestPreferencesState = state
                applyPreferences(state)
            }
        }
    }

    private fun applyPreferences(state: ReaderPreferencesState) {
        _uiState.update { current ->
            val activeUri = current.currentDocumentUri
            current.copy(
                readingMode = state.readingMode,
                bookshelf = state.bookshelf.sortedByDescending { it.lastOpenedAt },
                currentPage = activeUri?.let { state.pagedPositions[it] } ?: current.currentPage,
                currentScrollProgress = activeUri?.let { state.scrollPositions[it] } ?: current.currentScrollProgress
            )
        }
    }

    private fun loadDocument(uriString: String, restored: Boolean) {
        persistCurrentProgress()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    isRestoredFromHistory = restored,
                    currentDocumentUri = uriString
                )
            }
            when (val result = repository.openDocument(uriString)) {
                is DocumentLoadResult.Success -> showDocument(result.document, restored)
                is DocumentLoadResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.error,
                            route = ReaderRoute.Bookshelf,
                            document = if (restored) null else it.document,
                            isRestoredFromHistory = restored,
                            currentDocumentUri = if (restored) null else it.currentDocumentUri
                        )
                    }
                }
            }
        }
    }

    private fun showDocument(document: MarkdownDocument, restored: Boolean) {
        val updatedBookshelf = latestPreferencesState.bookshelf
            .filterNot { it.uri == document.uri }
            .toMutableList()
            .apply {
                add(
                    0,
                    StoredDocument(
                        uri = document.uri,
                        fileName = document.fileName,
                        importedAt = latestPreferencesState.bookshelf
                            .firstOrNull { it.uri == document.uri }
                            ?.importedAt
                            ?: System.currentTimeMillis(),
                        lastOpenedAt = System.currentTimeMillis()
                    )
                )
            }
        val restoredPage = latestPreferencesState.pagedPositions[document.uri] ?: 0
        val restoredScroll = latestPreferencesState.scrollPositions[document.uri] ?: ScrollProgress()

        _uiState.update {
            it.copy(
                route = ReaderRoute.Reader,
                isLoading = false,
                bookshelf = updatedBookshelf,
                currentDocumentUri = document.uri,
                document = document,
                currentPage = restoredPage,
                currentScrollProgress = restoredScroll,
                error = null,
                isRestoredFromHistory = restored
            )
        }
        viewModelScope.launch {
            preferences.saveBookshelf(updatedBookshelf)
            preferences.saveLastOpenedUri(document.uri)
        }
    }

    private fun persistPagedPosition(uri: String, page: Int) {
        val updated = latestPreferencesState.pagedPositions.toMutableMap().apply {
            this[uri] = page.coerceAtLeast(0)
        }
        latestPreferencesState = latestPreferencesState.copy(pagedPositions = updated)
        viewModelScope.launch {
            preferences.savePagedPositions(updated)
        }
    }

    private fun persistScrollPosition(uri: String, progress: ScrollProgress) {
        val updated = latestPreferencesState.scrollPositions.toMutableMap().apply {
            this[uri] = progress
        }
        latestPreferencesState = latestPreferencesState.copy(scrollPositions = updated)
        viewModelScope.launch {
            preferences.saveScrollPositions(updated)
        }
    }

    companion object {
        fun factory(
            repository: MarkdownDocumentRepository,
            preferences: ReaderPreferences
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReaderViewModel(repository, preferences) as T
            }
        }
    }
}
