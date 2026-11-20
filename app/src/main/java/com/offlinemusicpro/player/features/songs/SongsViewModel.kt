package com.offlinemusicpro.player.features.songs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.offlinemusicpro.player.OfflineMusicApp
import com.offlinemusicpro.player.data.local.PlaylistEntity
import com.offlinemusicpro.player.data.model.Song
import com.offlinemusicpro.player.playback.MusicPlayerManager
import com.offlinemusicpro.player.playback.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortField(val label: String) {
    TITLE("Title"), ARTIST("Artist"), DURATION("Duration"), DATE_ADDED("Date added")
}

sealed interface LibraryFilter {
    data object All : LibraryFilter
    data object Favorites : LibraryFilter
    data object RecentlyAdded : LibraryFilter
    data object RecentlyPlayed : LibraryFilter
    data class Playlist(val id: Long) : LibraryFilter
}

data class SongsUiState(
    val songs: List<Song> = emptyList(),
    val librarySongs: List<Song> = emptyList(),
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
    val query: String = "",
    val sortField: SortField = SortField.TITLE,
    val ascending: Boolean = true,
    val filter: LibraryFilter = LibraryFilter.All,
    val selectedIds: Set<Long> = emptySet(),
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val favoriteIds: Set<Long> = emptySet(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val folders: List<String> = emptyList(),
    val excludedFolders: Set<String> = emptySet(),
    val playback: PlaybackState = PlaybackState()
) {
    val selectionMode: Boolean get() = selectedIds.isNotEmpty()
}

class SongsViewModel(application: Application) : AndroidViewModel(application) {

    private data class Controls(
        val query: String = "",
        val sort: SortField = SortField.TITLE,
        val ascending: Boolean = true,
        val filter: LibraryFilter = LibraryFilter.All
    )

    private data class Library(
        val all: List<Song>,
        val songs: List<Song>,
        val favorites: Set<Long>,
        val recent: List<Long>,
        val playlistSongs: Map<Long, Set<Long>>,
        val excluded: Set<String>
    )

    private val container = (application as OfflineMusicApp).container
    private val repository = container.songsRepository
    private val library = container.libraryRepository
    private val settings = container.settingsRepository
    val player: MusicPlayerManager = container.playerManager

    private val allSongs = MutableStateFlow<List<Song>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val controls = MutableStateFlow(Controls())
    private val selected = MutableStateFlow<Set<Long>>(emptySet())
    private var scanJob: Job? = null
    private var restoreTried = false

    private val libraryFlow = combine(
        allSongs, settings.settings, library.favoriteIds, library.recentIds, library.playlistSongs
    ) { songs, s, favorites, recent, playlistSongs ->
        Library(
            all = songs,
            songs = songs.filter { it.folder !in s.excludedFolders },
            favorites = favorites,
            recent = recent,
            playlistSongs = playlistSongs,
            excluded = s.excludedFolders
        )
    }

    private val core = combine(libraryFlow, controls, selected) { l, c, sel -> Triple(l, c, sel) }

    val uiState: StateFlow<SongsUiState> =
        combine(core, player.state, loading, library.playlists) { (lib, c, sel), ps, isLoading, playlists ->
            val ids = lib.songs.mapTo(HashSet()) { it.id }
            SongsUiState(
                songs = lib.visible(c),
                librarySongs = lib.songs,
                totalCount = lib.songs.size,
                isLoading = isLoading,
                query = c.query,
                sortField = c.sort,
                ascending = c.ascending,
                filter = c.filter,
                selectedIds = sel.filterTo(HashSet()) { it in ids },
                currentSong = lib.all.firstOrNull { it.id == ps.currentSongId },
                isPlaying = ps.isPlaying,
                favoriteIds = lib.favorites,
                playlists = playlists,
                folders = lib.all.map { it.folder }.filter { it.isNotEmpty() }.distinct().sorted(),
                excludedFolders = lib.excluded,
                playback = ps
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongsUiState())

    init {
        player.connect()
    }

    /** Call once storage permission is granted. Safe to call repeatedly. */
    fun startScanning() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            repository.observeSongs()
                .catch { loading.value = false }
                .collect { songs ->
                    allSongs.value = songs
                    loading.value = false
                    if (!restoreTried && songs.isNotEmpty()) {
                        restoreTried = true
                        restoreLastPlayed(songs)
                    }
                }
        }
    }

    private suspend fun restoreLastPlayed(songs: List<Song>) {
        val (id, position) = settings.lastPlayed() ?: return
        val ordered = songs.sortedBy { it.title.lowercase() }
        val index = ordered.indexOfFirst { it.id == id }
        if (index >= 0) player.restoreIfIdle(ordered, index, position)
    }

    // ----- browsing controls -----

    fun setQuery(query: String) = controls.update { it.copy(query = query) }

    fun setSort(field: SortField) = controls.update {
        if (it.sort == field) it.copy(ascending = !it.ascending)
        else it.copy(sort = field, ascending = field != SortField.DATE_ADDED)
    }

    fun setFilter(filter: LibraryFilter) = controls.update {
        if (filter == LibraryFilter.RecentlyAdded) {
            it.copy(filter = filter, sort = SortField.DATE_ADDED, ascending = false)
        } else {
            it.copy(filter = filter)
        }
    }

    // ----- selection -----

    fun toggleSelection(id: Long) = selected.update { if (id in it) it - id else it + id }
    fun selectAllVisible(ids: List<Long>) = selected.update { it + ids }
    fun clearSelection() { selected.value = emptySet() }

    fun selectedSongs(): List<Song> {
        val s = uiState.value
        return s.songs.filter { it.id in s.selectedIds }
    }

    // ----- playback -----

    /** Plays the tapped song and queues the rest of the visible list after it. */
    fun onSongTapped(song: Song) {
        val list = uiState.value.songs
        val index = list.indexOfFirst { it.id == song.id }
        if (index >= 0) player.play(list, index)
    }

    fun playSongs(songs: List<Song>) = player.play(songs, 0)
    fun playNext(songs: List<Song>) = player.playNext(songs)
    fun addToQueue(songs: List<Song>) = player.addToQueue(songs)
    fun togglePlayPause() = player.togglePlayPause()
    fun skipNext() = player.skipNext()

    // ----- favorites & playlists -----

    fun toggleFavorites(ids: List<Long>) {
        viewModelScope.launch { library.toggleFavorites(ids) }
    }

    fun createPlaylist(name: String, thenAdd: List<Long>) {
        viewModelScope.launch {
            val id = library.createPlaylist(name)
            if (thenAdd.isNotEmpty()) library.addToPlaylist(id, thenAdd)
        }
    }

    fun addToPlaylist(playlistId: Long, ids: List<Long>) {
        viewModelScope.launch { library.addToPlaylist(playlistId, ids) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            library.deletePlaylist(id)
            controls.update { if (it.filter == LibraryFilter.Playlist(id)) it.copy(filter = LibraryFilter.All) else it }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { library.clearHistory() }
    }

    // ----- list building -----

    private fun Library.visible(c: Controls): List<Song> {
        var result: List<Song> = songs
        when (val f = c.filter) {
            LibraryFilter.All -> Unit
            LibraryFilter.Favorites -> result = result.filter { it.id in favorites }
            LibraryFilter.RecentlyAdded -> {
                val cutoff = System.currentTimeMillis() / 1000 - RECENT_WINDOW_SEC
                result = result.filter { it.dateAddedSec >= cutoff }
            }
            LibraryFilter.RecentlyPlayed -> {
                val order = recent.withIndex().associate { it.value to it.index }
                result = result.filter { it.id in order }.sortedBy { order[it.id] }
            }
            is LibraryFilter.Playlist -> {
                val ids = playlistSongs[f.id].orEmpty()
                result = result.filter { it.id in ids }
            }
        }
        val q = c.query.trim()
        if (q.isNotEmpty()) {
            result = result.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.artist.contains(q, ignoreCase = true) ||
                    it.album.contains(q, ignoreCase = true)
            }
        }
        if (c.filter == LibraryFilter.RecentlyPlayed) return result // keep "most recent first"
        val comparator: Comparator<Song> = when (c.sort) {
            SortField.TITLE -> compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
            SortField.ARTIST -> compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }
            SortField.DURATION -> compareBy<Song> { it.durationMs }
            SortField.DATE_ADDED -> compareBy<Song> { it.dateAddedSec }
        }
        return result.sortedWith(if (c.ascending) comparator else comparator.reversed())
    }

    private companion object {
        const val RECENT_WINDOW_SEC = 30L * 24 * 60 * 60
    }
}
