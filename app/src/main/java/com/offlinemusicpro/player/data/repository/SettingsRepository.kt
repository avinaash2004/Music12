package com.offlinemusicpro.player.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.offlinemusicpro.player.playback.EqState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val accentIndex: Int = 0,
    val crossfadeSec: Int = 0,
    val shake: Boolean = false,
    val excludedFolders: Set<String> = emptySet(),
    val lyricsUris: Map<Long, String> = emptyMap()
)

class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val store get() = appContext.settingsStore

    private object Keys {
        val ACCENT = intPreferencesKey("accent")
        val CROSSFADE = intPreferencesKey("crossfade_sec")
        val SHAKE = booleanPreferencesKey("shake")
        val EXCLUDED = stringSetPreferencesKey("excluded_folders")
        val LYRICS = stringSetPreferencesKey("lyrics_uris")
        val EQ = stringPreferencesKey("eq_state")
        val LAST_SONG = longPreferencesKey("last_song")
        val LAST_POS = longPreferencesKey("last_pos")
    }

    private val prefs: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val settings: Flow<AppSettings> = prefs.map { p ->
        AppSettings(
            accentIndex = p[Keys.ACCENT] ?: 0,
            crossfadeSec = p[Keys.CROSSFADE] ?: 0,
            shake = p[Keys.SHAKE] ?: false,
            excludedFolders = p[Keys.EXCLUDED] ?: emptySet(),
            lyricsUris = (p[Keys.LYRICS] ?: emptySet()).mapNotNull { entry ->
                val i = entry.indexOf('|')
                if (i < 0) null else entry.substring(0, i).toLongOrNull()?.let { it to entry.substring(i + 1) }
            }.toMap()
        )
    }

    suspend fun setAccent(index: Int) { store.edit { it[Keys.ACCENT] = index } }
    suspend fun setCrossfade(seconds: Int) { store.edit { it[Keys.CROSSFADE] = seconds } }
    suspend fun setShake(enabled: Boolean) { store.edit { it[Keys.SHAKE] = enabled } }
    suspend fun setExcluded(folders: Set<String>) { store.edit { it[Keys.EXCLUDED] = folders } }

    suspend fun setLyricsUri(songId: Long, uri: String) {
        store.edit { p ->
            val kept = (p[Keys.LYRICS] ?: emptySet()).filterNot { it.startsWith("$songId|") }.toMutableSet()
            kept += "$songId|$uri"
            p[Keys.LYRICS] = kept
        }
    }

    suspend fun eqState(): EqState = EqState.fromJson(prefs.first()[Keys.EQ])
    suspend fun saveEq(state: EqState) { store.edit { it[Keys.EQ] = state.toJson() } }

    suspend fun saveResume(songId: Long, positionMs: Long) {
        store.edit {
            it[Keys.LAST_SONG] = songId
            it[Keys.LAST_POS] = positionMs
        }
    }

    /** Last played song id and position, or null if nothing was played yet. */
    suspend fun lastPlayed(): Pair<Long, Long>? {
        val p = prefs.first()
        val id = p[Keys.LAST_SONG] ?: return null
        return id to (p[Keys.LAST_POS] ?: 0L)
    }
}
