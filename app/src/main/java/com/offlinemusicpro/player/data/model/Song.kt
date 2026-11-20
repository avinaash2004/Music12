package com.offlinemusicpro.player.data.model

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val dateAddedSec: Long,
    val sizeBytes: Long,
    val folder: String,
    val displayName: String
) {
    val uri: Uri
        get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}
