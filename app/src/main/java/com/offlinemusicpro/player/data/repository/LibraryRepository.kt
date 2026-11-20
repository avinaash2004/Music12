package com.offlinemusicpro.player.data.repository

import com.offlinemusicpro.player.data.local.EqPresetEntity
import com.offlinemusicpro.player.data.local.FavoriteEntity
import com.offlinemusicpro.player.data.local.HistoryEntity
import com.offlinemusicpro.player.data.local.LibraryDao
import com.offlinemusicpro.player.data.local.PlaylistEntity
import com.offlinemusicpro.player.data.local.PlaylistSongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LibraryRepository(private val dao: LibraryDao) {

    val favoriteIds: Flow<Set<Long>> = dao.observeFavoriteIds().map { it.toSet() }
    val recentIds: Flow<List<Long>> = dao.observeRecentIds()
    val playlists: Flow<List<PlaylistEntity>> = dao.observePlaylists()
    val playlistSongs: Flow<Map<Long, Set<Long>>> = dao.observePlaylistSongs().map { rows ->
        rows.groupBy({ it.playlistId }, { it.songId }).mapValues { it.value.toSet() }
    }
    val customPresets: Flow<List<EqPresetEntity>> = dao.observePresets()

    /** If every id is already a favorite, removes them all; otherwise favorites the missing ones. */
    suspend fun toggleFavorites(ids: List<Long>) {
        if (ids.isEmpty()) return
        val existing = dao.favoritesAmong(ids).toSet()
        if (existing.size == ids.toSet().size) {
            dao.removeFavorites(ids)
        } else {
            val now = System.currentTimeMillis()
            dao.addFavorites(ids.filter { it !in existing }.map { FavoriteEntity(it, now) })
        }
    }

    suspend fun recordPlayed(songId: Long) {
        dao.insertHistory(HistoryEntity(songId = songId, playedAt = System.currentTimeMillis()))
        dao.trimHistory()
    }

    suspend fun clearHistory() = dao.clearHistory()

    suspend fun createPlaylist(name: String): Long =
        dao.insertPlaylist(PlaylistEntity(name = name.trim().ifEmpty { "Playlist" }))

    suspend fun deletePlaylist(id: Long) {
        dao.deletePlaylistSongs(id)
        dao.deletePlaylist(id)
    }

    suspend fun addToPlaylist(playlistId: Long, songIds: List<Long>) {
        val now = System.currentTimeMillis()
        dao.addToPlaylist(songIds.map { PlaylistSongEntity(playlistId, it, now) })
    }

    suspend fun savePreset(preset: EqPresetEntity) = dao.savePreset(preset)
    suspend fun deletePreset(name: String) = dao.deletePreset(name)
}
