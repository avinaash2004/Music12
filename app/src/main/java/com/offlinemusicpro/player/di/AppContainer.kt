package com.offlinemusicpro.player.di

import android.content.Context
import androidx.room.Room
import com.offlinemusicpro.player.data.local.AppDatabase
import com.offlinemusicpro.player.data.repository.BackupRepository
import com.offlinemusicpro.player.data.repository.LibraryRepository
import com.offlinemusicpro.player.data.repository.SettingsRepository
import com.offlinemusicpro.player.data.repository.SongsRepository
import com.offlinemusicpro.player.data.scanner.MediaStoreScanner
import com.offlinemusicpro.player.features.lyrics.LyricsRepository
import com.offlinemusicpro.player.playback.MusicPlayerManager

/** Lightweight manual dependency container (no DI framework, no annotation processing for DI). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val scanner: MediaStoreScanner by lazy { MediaStoreScanner(appContext) }
    val songsRepository: SongsRepository by lazy { SongsRepository(scanner) }

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "offline_music.db").build()
    }
    val libraryRepository: LibraryRepository by lazy { LibraryRepository(database.libraryDao()) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }
    val backupRepository: BackupRepository by lazy {
        BackupRepository(database.libraryDao(), settingsRepository, scanner)
    }
    val lyricsRepository: LyricsRepository by lazy { LyricsRepository(appContext) }
    val playerManager: MusicPlayerManager by lazy { MusicPlayerManager(appContext) }
}
