package com.example.markdownreader.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.IllegalCharsetNameException

interface MarkdownDocumentDataSource {
    suspend fun openDocument(uriString: String): DocumentLoadResult
}

class MarkdownDocumentRepository(
    context: Context,
    private val preferences: ReaderPreferencesStore
) : MarkdownDocumentDataSource {
    private val contentResolver: ContentResolver = context.contentResolver

    override suspend fun openDocument(uriString: String): DocumentLoadResult {
        val uri = Uri.parse(uriString)
        return try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return DocumentLoadResult.Error(ReadError.OpenFailed)

            val decoded = CharsetDecoder.decode(bytes)
                ?: return DocumentLoadResult.Error(ReadError.DecodeFailed)

            if (decoded.text.isBlank()) {
                preferences.saveLastOpenedUri(uri.toString())
                return DocumentLoadResult.Error(ReadError.EmptyFile)
            }

            val document = MarkdownDocument(
                uri = uri.toString(),
                fileName = queryDisplayName(uri) ?: uri.lastPathSegment ?: uri.toString(),
                content = decoded.text,
                encoding = decoded.charsetName
            )
            preferences.saveLastOpenedUri(uri.toString())
            DocumentLoadResult.Success(document)
        } catch (_: SecurityException) {
            preferences.clearLastOpenedUri()
            DocumentLoadResult.Error(ReadError.PermissionDenied)
        } catch (_: FileNotFoundException) {
            preferences.clearLastOpenedUri()
            DocumentLoadResult.Error(ReadError.OpenFailed)
        } catch (_: IOException) {
            DocumentLoadResult.Error(ReadError.OpenFailed)
        } catch (_: IllegalCharsetNameException) {
            DocumentLoadResult.Error(ReadError.DecodeFailed)
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        val cursor: Cursor = contentResolver.query(uri, projection, null, null, null) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index == -1) return null
            return it.getString(index)
        }
    }
}

data class MarkdownDocument(
    val uri: String,
    val fileName: String,
    val content: String,
    val encoding: String
)

sealed interface DocumentLoadResult {
    data class Success(val document: MarkdownDocument) : DocumentLoadResult
    data class Error(val error: ReadError) : DocumentLoadResult
}

enum class ReadError {
    OpenFailed,
    PermissionDenied,
    EmptyFile,
    DecodeFailed
}
