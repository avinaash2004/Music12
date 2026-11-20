package com.offlinemusicpro.player.data.repository

import com.offlinemusicpro.player.data.local.EqPresetEntity
import com.offlinemusicpro.player.data.local.FavoriteEntity
import com.offlinemusicpro.player.data.local.HistoryEntity
import com.offlinemusicpro.player.data.local.LibraryDao
import com.offlinemusicpro.player.data.local.PlaylistEntity
import com.offlinemusicpro.player.data.local.PlaylistSongEntity
import com.offlinemusicpro.player.data.model.Song
import com.offlinemusicpro.player.data.scanner.MediaStoreScanner
import com.offlinemusicpro.player.playback.EqState
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

data class RestoreResult(val favorites: Int, val history: Int, val playlists: Int, val presets: Int)

/**
 * JSON backup of favorites, history, playlists, equalizer presets and settings.
 * Songs are identified by title|artist|duration so a backup survives MediaStore id changes.
 */
class BackupRepository(
    private val dao: LibraryDao,
    private val settings: SettingsRepository,
    private val scanner: MediaStoreScanner
) {
    private fun key(s: Song) = "${s.title.lowercase()}|${s.artist.lowercase()}|${s.durationMs / 1000}"

    suspend fun export(): String {
        val songs = scanner.scan()
        val keyById = songs.associate { it.id to key(it) }
        val root = JSONObject().put("app", "OfflineMusicPro").put("version", 1)

        root.put("favorites", JSONArray().also { arr ->
            dao.allFavorites().forEach { f ->
                keyById[f.songId]?.let { arr.put(JSONObject().put("key", it).put("addedAt", f.addedAt)) }
            }
        })
        root.put("history", JSONArray().also { arr ->
            dao.allHistory().forEach { h ->
                keyById[h.songId]?.let { arr.put(JSONObject().put("key", it).put("playedAt", h.playedAt)) }
            }
        })
        val playlistSongs = dao.allPlaylistSongs().groupBy { it.playlistId }
        root.put("playlists", JSONArray().also { arr ->
            dao.allPlaylists().forEach { p ->
                val keys = JSONArray()
                playlistSongs[p.id].orEmpty().forEach { ps -> keyById[ps.songId]?.let { keys.put(it) } }
                arr.put(JSONObject().put("name", p.name).put("keys", keys))
            }
        })
        root.put("eqPresets", JSONArray().also { arr ->
            dao.allPresets().forEach { p ->
                arr.put(
                    JSONObject().put("name", p.name).put("levels", p.levels).put("bass", p.bass)
                        .put("virtualizer", p.virtualizer).put("treble", p.treble)
                )
            }
        })
        val s = settings.settings.first()
        root.put(
            "settings",
            JSONObject()
                .put("accent", s.accentIndex)
                .put("crossfadeSec", s.crossfadeSec)
                .put("shake", s.shake)
                .put("excludedFolders", JSONArray(s.excludedFolders.toList()))
                .put("eq", settings.eqState().toJson())
        )
        return root.toString(2)
    }

    suspend fun import(json: String): RestoreResult {
        val root = JSONObject(json)
        require(root.optString("app") == "OfflineMusicPro") { "Not an Offline Music Pro backup file" }
        val idByKey = HashMap<String, Long>()
        scanner.scan().forEach { idByKey.putIfAbsent(key(it), it.id) }

        val favorites = ArrayList<FavoriteEntity>()
        root.optJSONArray("favorites")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                idByKey[o.getString("key")]?.let { favorites += FavoriteEntity(it, o.optLong("addedAt")) }
            }
        }
        dao.addFavorites(favorites)

        val history = ArrayList<HistoryEntity>()
        root.optJSONArray("history")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                idByKey[o.getString("key")]?.let { history += HistoryEntity(songId = it, playedAt = o.optLong("playedAt")) }
            }
        }
        dao.clearHistory()
        dao.insertHistoryAll(history)
        dao.trimHistory()

        var playlistCount = 0
        val existing = dao.allPlaylists().associateBy { it.name }
        root.optJSONArray("playlists")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.getString("name")
                val id = existing[name]?.id ?: dao.insertPlaylist(PlaylistEntity(name = name))
                val keys = o.optJSONArray("keys") ?: JSONArray()
                val now = System.currentTimeMillis()
                val rows = (0 until keys.length()).mapNotNull { idByKey[keys.getString(it)] }
                    .map { PlaylistSongEntity(id, it, now) }
                dao.addToPlaylist(rows)
                playlistCount++
            }
        }

        var presetCount = 0
        root.optJSONArray("eqPresets")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                dao.savePreset(
                    EqPresetEntity(
                        o.getString("name"), o.getString("levels"), o.optInt("bass"),
                        o.optInt("virtualizer"), o.optInt("treble")
                    )
                )
                presetCount++
            }
        }

        root.optJSONObject("settings")?.let { s ->
            settings.setAccent(s.optInt("accent", 0))
            settings.setCrossfade(s.optInt("crossfadeSec", 0))
            settings.setShake(s.optBoolean("shake", false))
            val folders = s.optJSONArray("excludedFolders")
            settings.setExcluded(
                if (folders == null) emptySet() else (0 until folders.length()).map { folders.getString(it) }.toSet()
            )
            s.optString("eq").takeIf { it.isNotBlank() }?.let { settings.saveEq(EqState.fromJson(it)) }
        }
        return RestoreResult(favorites.size, history.size, playlistCount, presetCount)
    }
}
