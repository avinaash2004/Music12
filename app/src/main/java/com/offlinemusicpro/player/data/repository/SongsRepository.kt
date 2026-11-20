package com.offlinemusicpro.player.data.repository

import com.offlinemusicpro.player.data.model.Song
import com.offlinemusicpro.player.data.scanner.MediaStoreScanner
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class SongsRepository(private val scanner: MediaStoreScanner) {

    /** Current library, then a fresh list whenever the system audio index changes. */
    @OptIn(FlowPreview::class)
    fun observeSongs(): Flow<List<Song>> = flow {
        emit(scanner.scan())
        emitAll(scanner.changes().debounce(1_000).map { scanner.scan() })
    }
}
