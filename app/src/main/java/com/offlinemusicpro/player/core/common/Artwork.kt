package com.offlinemusicpro.player.core.common

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.offlinemusicpro.player.core.theme.CardDark
import com.offlinemusicpro.player.core.theme.SurfaceDark
import com.offlinemusicpro.player.data.model.Song
import com.offlinemusicpro.player.data.scanner.ArtworkLoader

@Composable
fun Artwork(
    song: Song,
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = 12.dp
) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val bitmap by produceState<Bitmap?>(
        initialValue = ArtworkLoader.cached(song, px),
        key1 = song.id,
        key2 = px
    ) {
        value = ArtworkLoader.load(context, song, px)
    }
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(CardDark, SurfaceDark))),
        contentAlignment = Alignment.Center
    ) {
        val art = bitmap
        if (art != null) {
            Image(
                bitmap = art.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}
