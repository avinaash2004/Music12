package com.offlinemusicpro.player.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val songId: Long, val addedAt: Long)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val playedAt: Long
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongEntity(val playlistId: Long, val songId: Long, val addedAt: Long)

@Entity(tableName = "eq_presets")
data class EqPresetEntity(
    @PrimaryKey val name: String,
    val levels: String,
    val bass: Int,
    val virtualizer: Int,
    val treble: Int
)

@Dao
interface LibraryDao {
    // favorites
    @Query("SELECT songId FROM favorites")
    fun observeFavoriteIds(): Flow<List<Long>>

    @Query("SELECT * FROM favorites")
    suspend fun allFavorites(): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorites(items: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE songId IN (:ids)")
    suspend fun removeFavorites(ids: List<Long>)

    @Query("SELECT songId FROM favorites WHERE songId IN (:ids)")
    suspend fun favoritesAmong(ids: List<Long>): List<Long>

    // history
    @Insert
    suspend fun insertHistory(item: HistoryEntity)

    @Insert
    suspend fun insertHistoryAll(items: List<HistoryEntity>)

    @Query("SELECT songId FROM history GROUP BY songId ORDER BY MAX(playedAt) DESC LIMIT 200")
    fun observeRecentIds(): Flow<List<Long>>

    @Query("SELECT * FROM history ORDER BY playedAt DESC")
    suspend fun allHistory(): List<HistoryEntity>

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY playedAt DESC LIMIT 2000)")
    suspend fun trimHistory()

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    // playlists
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists")
    suspend fun allPlaylists(): List<PlaylistEntity>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id")
    suspend fun deletePlaylistSongs(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToPlaylist(items: List<PlaylistSongEntity>)

    @Query("SELECT * FROM playlist_songs")
    fun observePlaylistSongs(): Flow<List<PlaylistSongEntity>>

    @Query("SELECT * FROM playlist_songs")
    suspend fun allPlaylistSongs(): List<PlaylistSongEntity>

    // equalizer presets
    @Query("SELECT * FROM eq_presets ORDER BY name COLLATE NOCASE")
    fun observePresets(): Flow<List<EqPresetEntity>>

    @Query("SELECT * FROM eq_presets")
    suspend fun allPresets(): List<EqPresetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePreset(preset: EqPresetEntity)

    @Query("DELETE FROM eq_presets WHERE name = :name")
    suspend fun deletePreset(name: String)
}

@Database(
    entities = [
        FavoriteEntity::class, HistoryEntity::class, PlaylistEntity::class,
        PlaylistSongEntity::class, EqPresetEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
}
