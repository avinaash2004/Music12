package com.offlinemusicpro.player

/** The app has one bottom tab (Songs). Every other screen is reached from inside it. */
enum class Route {
    SONGS, PLAYER, QUEUE, EQUALIZER, VISUALIZER, LYRICS,
    SETTINGS, EXCLUDED, DUPLICATES, TRIMMER, BACKUP;

    fun parent(): Route? = when (this) {
        SONGS -> null
        PLAYER, SETTINGS -> SONGS
        QUEUE, EQUALIZER, VISUALIZER, LYRICS -> PLAYER
        EXCLUDED, DUPLICATES, TRIMMER, BACKUP -> SETTINGS
    }
}
