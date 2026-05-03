package com.example.markdownreader.ui.reader

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.markdownreader.R
import com.example.markdownreader.data.ReadError
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onImportFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    BackHandler(enabled = uiState.route == ReaderRoute.Reader) {
        viewModel.showBookshelf()
    }

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error.toMessage(context))
        viewModel.clearError()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (uiState.route == ReaderRoute.Reader) {
                        TextButton(onClick = viewModel::showBookshelf) {
                            Text(stringResource(R.string.reader_back_to_bookshelf))
                        }
                    }
                },
                title = {
                    Text(
                        text = if (uiState.route == ReaderRoute.Bookshelf) {
                            stringResource(R.string.bookshelf_title)
                        } else {
                            uiState.document?.fileName ?: stringResource(R.string.app_name)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    TextButton(onClick = onImportFile) {
                        Text(
                            stringResource(
                                if (uiState.route == ReaderRoute.Bookshelf) {
                                    R.string.reader_import_file
                                } else {
                                    R.string.reader_import_another
                                }
                            )
                        )
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> LoadingState(Modifier.padding(paddingValues))
            uiState.route == ReaderRoute.Bookshelf -> BookshelfScreen(
                bookshelf = uiState.bookshelf,
                onImportFile = onImportFile,
                onOpenDocument = viewModel::openDocument,
                modifier = Modifier.padding(paddingValues)
            )
            uiState.document != null -> ReaderContent(
                uiState = uiState,
                onReadingModeChange = viewModel::setReadingMode,
                onPageChanged = viewModel::updateCurrentPage,
                onScrollProgressChanged = viewModel::updateScrollProgress,
                modifier = Modifier.padding(paddingValues)
            )
            else -> BookshelfScreen(
                bookshelf = uiState.bookshelf,
                onImportFile = onImportFile,
                onOpenDocument = viewModel::openDocument,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.reader_loading))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderContent(
    uiState: ReaderUiState,
    onReadingModeChange: (ReadingMode) -> Unit,
    onPageChanged: (Int, Int) -> Unit,
    onScrollProgressChanged: (ScrollProgress) -> Unit,
    modifier: Modifier = Modifier
) {
    val document = uiState.document ?: return
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                selected = uiState.readingMode == ReadingMode.Scroll,
                onClick = { onReadingModeChange(ReadingMode.Scroll) },
                label = { Text(stringResource(R.string.reader_mode_scroll)) }
            )
            FilterChip(
                selected = uiState.readingMode == ReadingMode.Paged,
                onClick = { onReadingModeChange(ReadingMode.Paged) },
                label = { Text(stringResource(R.string.reader_mode_paged)) }
            )
        }
        Text(
            text = stringResource(R.string.reader_encoding_label, document.encoding),
            style = MaterialTheme.typography.labelLarge
        )
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            when (uiState.readingMode) {
                ReadingMode.Scroll -> ScrollReader(
                    content = document.content,
                    initialProgress = uiState.currentScrollProgress,
                    onScrollProgressChanged = onScrollProgressChanged,
                    modifier = Modifier.fillMaxSize()
                )
                ReadingMode.Paged -> PagedMarkdownReader(
                    content = document.content,
                    currentPage = uiState.currentPage,
                    onPageChanged = onPageChanged,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun ScrollReader(
    content: String,
    initialProgress: ScrollProgress,
    onScrollProgressChanged: (ScrollProgress) -> Unit,
    modifier: Modifier = Modifier
) {
    val blocks = remember(content) { parseMarkdownBlocks(content) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialProgress.firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = initialProgress.firstVisibleItemScrollOffset
    )

    LaunchedEffect(listState) {
        snapshotFlowProgress(listState).distinctUntilChanged().collect(onScrollProgressChanged)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MarkdownBlockContent(blocks = blocks)
        }
    }
}

private fun snapshotFlowProgress(listState: LazyListState) =
    snapshotFlow {
        ScrollProgress(
            firstVisibleItemIndex = listState.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
        )
    }

private fun ReadError.toMessage(context: Context): String {
    return when (this) {
        ReadError.OpenFailed -> context.getString(R.string.reader_error_open_failed)
        ReadError.PermissionDenied -> context.getString(R.string.reader_error_permission)
        ReadError.EmptyFile -> context.getString(R.string.reader_error_empty)
        ReadError.DecodeFailed -> context.getString(R.string.reader_error_decode)
    }
}
