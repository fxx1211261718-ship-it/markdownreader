package com.example.markdownreader.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.markdownreader.ui.reader.ReadingMode
import com.example.markdownreader.ui.reader.ScrollProgress
import com.example.markdownreader.ui.reader.StoredDocument
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerDataStore by preferencesDataStore(name = "reader_preferences")

interface ReaderPreferencesStore {
    val preferencesFlow: Flow<ReaderPreferencesState>
    suspend fun saveReadingMode(mode: ReadingMode)
    suspend fun saveLastOpenedUri(uri: String)
    suspend fun clearLastOpenedUri()
    suspend fun saveBookshelf(documents: List<StoredDocument>)
    suspend fun savePagedPositions(positions: Map<String, Int>)
    suspend fun saveScrollPositions(positions: Map<String, ScrollProgress>)
}

class ReaderPreferences(private val context: Context) : ReaderPreferencesStore {
    private val gson = Gson()

    override val preferencesFlow: Flow<ReaderPreferencesState> = context.readerDataStore.data.map { preferences ->
        ReaderPreferencesState(
            readingMode = preferences[Keys.READING_MODE]
                ?.let(ReadingMode::fromStorageValue)
                ?: ReadingMode.Scroll,
            lastOpenedUri = preferences[Keys.LAST_OPENED_URI],
            bookshelf = preferences[Keys.BOOKSHELF]
                ?.let(::decodeStoredDocuments)
                .orEmpty(),
            pagedPositions = preferences[Keys.PAGED_POSITIONS]
                ?.let(::decodePagedPositions)
                .orEmpty(),
            scrollPositions = preferences[Keys.SCROLL_POSITIONS]
                ?.let(::decodeScrollPositions)
                .orEmpty()
        )
    }

    override suspend fun saveReadingMode(mode: ReadingMode) {
        context.readerDataStore.edit { preferences ->
            preferences[Keys.READING_MODE] = mode.storageValue
        }
    }

    override suspend fun saveLastOpenedUri(uri: String) {
        context.readerDataStore.edit { preferences ->
            preferences[Keys.LAST_OPENED_URI] = uri
        }
    }

    override suspend fun clearLastOpenedUri() {
        context.readerDataStore.edit { preferences ->
            preferences.remove(Keys.LAST_OPENED_URI)
        }
    }

    override suspend fun saveBookshelf(documents: List<StoredDocument>) {
        context.readerDataStore.edit { preferences ->
            preferences[Keys.BOOKSHELF] = gson.toJson(documents)
        }
    }

    override suspend fun savePagedPositions(positions: Map<String, Int>) {
        context.readerDataStore.edit { preferences ->
            preferences[Keys.PAGED_POSITIONS] = gson.toJson(positions)
        }
    }

    override suspend fun saveScrollPositions(positions: Map<String, ScrollProgress>) {
        context.readerDataStore.edit { preferences ->
            preferences[Keys.SCROLL_POSITIONS] = gson.toJson(positions)
        }
    }

    private fun decodeStoredDocuments(value: String): List<StoredDocument> {
        return runCatching {
            gson.fromJson<List<StoredDocument>>(value, storedDocumentsType)
        }.getOrDefault(emptyList())
    }

    private fun decodePagedPositions(value: String): Map<String, Int> {
        return runCatching {
            gson.fromJson<Map<String, Int>>(value, pagedPositionsType)
        }.getOrDefault(emptyMap())
    }

    private fun decodeScrollPositions(value: String): Map<String, ScrollProgress> {
        return runCatching {
            gson.fromJson<Map<String, ScrollProgress>>(value, scrollPositionsType)
        }.getOrDefault(emptyMap())
    }

    private object Keys {
        val READING_MODE: Preferences.Key<String> = stringPreferencesKey("reading_mode")
        val LAST_OPENED_URI: Preferences.Key<String> = stringPreferencesKey("last_opened_uri")
        val BOOKSHELF: Preferences.Key<String> = stringPreferencesKey("bookshelf")
        val PAGED_POSITIONS: Preferences.Key<String> = stringPreferencesKey("paged_positions")
        val SCROLL_POSITIONS: Preferences.Key<String> = stringPreferencesKey("scroll_positions")
    }

    private companion object {
        val storedDocumentsType = object : TypeToken<List<StoredDocument>>() {}.type
        val pagedPositionsType = object : TypeToken<Map<String, Int>>() {}.type
        val scrollPositionsType = object : TypeToken<Map<String, ScrollProgress>>() {}.type
    }
}

data class ReaderPreferencesState(
    val readingMode: ReadingMode,
    val lastOpenedUri: String?,
    val bookshelf: List<StoredDocument> = emptyList(),
    val pagedPositions: Map<String, Int> = emptyMap(),
    val scrollPositions: Map<String, ScrollProgress> = emptyMap()
)
