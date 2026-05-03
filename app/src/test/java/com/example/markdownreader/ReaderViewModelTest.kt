package com.example.markdownreader

import com.example.markdownreader.data.DocumentLoadResult
import com.example.markdownreader.data.MarkdownDocument
import com.example.markdownreader.data.MarkdownDocumentDataSource
import com.example.markdownreader.data.ReadError
import com.example.markdownreader.data.ReaderPreferencesState
import com.example.markdownreader.data.ReaderPreferencesStore
import com.example.markdownreader.ui.reader.ReaderRoute
import com.example.markdownreader.ui.reader.ReaderViewModel
import com.example.markdownreader.ui.reader.ReadingMode
import com.example.markdownreader.ui.reader.ScrollProgress
import com.example.markdownreader.ui.reader.StoredDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun updatesReadingMode() = runTest {
        val preferences = FakeReaderPreferences()
        val repository = FakeRepository(DocumentLoadResult.Error(ReadError.OpenFailed))
        val viewModel = ReaderViewModel(repository, preferences)

        viewModel.setReadingMode(ReadingMode.Paged)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ReadingMode.Paged, viewModel.uiState.value.readingMode)
    }

    @Test
    fun loadsDocumentSuccessfullyAndShowsReaderRoute() = runTest {
        val preferences = FakeReaderPreferences()
        val repository = FakeRepository(
            DocumentLoadResult.Success(
                MarkdownDocument("content://demo/doc", "test.md", "# title", "UTF-8")
            )
        )
        val viewModel = ReaderViewModel(repository, preferences)

        viewModel.openDocument("content://demo/doc")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("test.md", viewModel.uiState.value.document?.fileName)
        assertEquals(ReaderRoute.Reader, viewModel.uiState.value.route)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun showsErrorWhenDocumentFails() = runTest {
        val preferences = FakeReaderPreferences()
        val repository = FakeRepository(DocumentLoadResult.Error(ReadError.OpenFailed))
        val viewModel = ReaderViewModel(repository, preferences)

        viewModel.openDocument("content://demo/doc")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ReadError.OpenFailed, viewModel.uiState.value.error)
        assertEquals(ReaderRoute.Bookshelf, viewModel.uiState.value.route)
    }

    @Test
    fun deduplicatesBookshelfAndRestoresPerDocumentProgress() = runTest {
        val preferences = FakeReaderPreferences(
            initialState = ReaderPreferencesState(
                readingMode = ReadingMode.Scroll,
                lastOpenedUri = null,
                bookshelf = listOf(
                    StoredDocument("content://demo/doc", "old.md", 1L, 2L)
                ),
                pagedPositions = mapOf("content://demo/doc" to 4),
                scrollPositions = mapOf(
                    "content://demo/doc" to ScrollProgress(3, 24)
                )
            )
        )
        val repository = FakeRepository(
            DocumentLoadResult.Success(
                MarkdownDocument("content://demo/doc", "new.md", "# title", "UTF-8")
            )
        )
        val viewModel = ReaderViewModel(repository, preferences)

        viewModel.openDocument("content://demo/doc")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.bookshelf.size)
        assertEquals("new.md", viewModel.uiState.value.bookshelf.first().fileName)
        assertEquals(4, viewModel.uiState.value.currentPage)
        assertEquals(3, viewModel.uiState.value.currentScrollProgress.firstVisibleItemIndex)
    }

    @Test
    fun returningToBookshelfKeepsDocumentProgressPerUri() = runTest {
        val preferences = FakeReaderPreferences()
        val repository = FakeRepository(
            DocumentLoadResult.Success(
                MarkdownDocument("content://demo/doc", "test.md", "# title", "UTF-8")
            )
        )
        val viewModel = ReaderViewModel(repository, preferences)

        viewModel.openDocument("content://demo/doc")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.updateScrollProgress(ScrollProgress(6, 18))
        viewModel.showBookshelf()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ReaderRoute.Bookshelf, viewModel.uiState.value.route)
        assertEquals(6, preferences.lastSavedScrollPositions["content://demo/doc"]?.firstVisibleItemIndex)
    }

    @Test
    fun persistsClampedPagedPosition() = runTest {
        val preferences = FakeReaderPreferences()
        val repository = FakeRepository(
            DocumentLoadResult.Success(
                MarkdownDocument("content://demo/doc", "test.md", "# title", "UTF-8")
            )
        )
        val viewModel = ReaderViewModel(repository, preferences)

        viewModel.openDocument("content://demo/doc")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.setReadingMode(ReadingMode.Paged)
        viewModel.updateCurrentPage(page = 99, pageCount = 3)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.currentPage)
        assertEquals(2, preferences.lastSavedPagedPositions["content://demo/doc"])
    }
}

private class FakeReaderPreferences(
    initialState: ReaderPreferencesState = ReaderPreferencesState(ReadingMode.Scroll, null)
) : ReaderPreferencesStore {
    private val mutableFlow = MutableStateFlow(initialState)
    var lastSavedPagedPositions: Map<String, Int> = initialState.pagedPositions
    var lastSavedScrollPositions: Map<String, ScrollProgress> = initialState.scrollPositions

    override val preferencesFlow: Flow<ReaderPreferencesState>
        get() = mutableFlow

    override suspend fun saveReadingMode(mode: ReadingMode) {
        mutableFlow.value = mutableFlow.value.copy(readingMode = mode)
    }

    override suspend fun saveLastOpenedUri(uri: String) {
        mutableFlow.value = mutableFlow.value.copy(lastOpenedUri = uri)
    }

    override suspend fun clearLastOpenedUri() {
        mutableFlow.value = mutableFlow.value.copy(lastOpenedUri = null)
    }

    override suspend fun saveBookshelf(documents: List<StoredDocument>) {
        mutableFlow.value = mutableFlow.value.copy(bookshelf = documents)
    }

    override suspend fun savePagedPositions(positions: Map<String, Int>) {
        lastSavedPagedPositions = positions
        mutableFlow.value = mutableFlow.value.copy(pagedPositions = positions)
    }

    override suspend fun saveScrollPositions(positions: Map<String, ScrollProgress>) {
        lastSavedScrollPositions = positions
        mutableFlow.value = mutableFlow.value.copy(scrollPositions = positions)
    }
}

private class FakeRepository(
    private val result: DocumentLoadResult
) : MarkdownDocumentDataSource {
    override suspend fun openDocument(uriString: String): DocumentLoadResult = result
}
