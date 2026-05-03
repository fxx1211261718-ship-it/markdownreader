package com.example.markdownreader

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.markdownreader.data.MarkdownDocumentRepository
import com.example.markdownreader.data.ReaderPreferences
import com.example.markdownreader.ui.reader.ReaderScreen
import com.example.markdownreader.ui.reader.ReaderViewModel
import com.example.markdownreader.ui.theme.MarkdownreaderTheme

class MainActivity : ComponentActivity() {
    private var initialDocumentUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialDocumentUri = intent.extractDocumentUri()
        setContent {
            MarkdownreaderTheme {
                Surface(
                    modifier = Modifier,
                    color = MaterialTheme.colorScheme.background
                ) {
                    MarkdownReaderApp(
                        initialDocumentUri = initialDocumentUri,
                        consumeInitialDocumentUri = { initialDocumentUri = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialDocumentUri = intent.extractDocumentUri()
    }
}

private fun Intent?.extractDocumentUri(): String? {
    if (this == null) return null
    return when (action) {
        Intent.ACTION_VIEW -> dataString
        Intent.ACTION_SEND -> extractStreamUri()?.toString()
        else -> dataString
    }
}

private fun Intent.extractStreamUri(): android.net.Uri? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_STREAM)
    }
}

@Composable
private fun MarkdownReaderApp(
    initialDocumentUri: String?,
    consumeInitialDocumentUri: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember(context) { ReaderPreferences(context) }
    val repository = remember(context) { MarkdownDocumentRepository(context, preferences) }
    val viewModel: ReaderViewModel = viewModel(
        factory = ReaderViewModel.factory(repository, preferences)
    )
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importDocument(it.toString()) }
    }

    androidx.compose.runtime.LaunchedEffect(initialDocumentUri) {
        initialDocumentUri?.let {
            viewModel.importDocument(it)
            consumeInitialDocumentUri()
        }
    }

    ReaderScreen(
        viewModel = viewModel,
        onImportFile = {
            launcher.launch(arrayOf("text/markdown", "text/plain", "text/*"))
        }
    )
}
