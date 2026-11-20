package com.offlinemusicpro.player.features.settings

import com.offlinemusicpro.player.data.model.Song

/** Songs with the same title, artist and (roughly) the same length are treated as duplicates. */
fun findDuplicateGroups(songs: List<Song>): List<List<Song>> =
    songs.groupBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}|${it.durationMs / 2000}" }
        .values
        .filter { it.size > 1 }
        .sortedBy { it.first().title.lowercase() }
