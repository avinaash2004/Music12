package com.offlinemusicpro.player.playback

import org.json.JSONArray
import org.json.JSONObject

/** Equalizer + effects settings. Levels are in millibels, one per device EQ band. */
data class EqState(
    val enabled: Boolean = false,
    val levelsMb: List<Int> = emptyList(),
    val bass: Int = 0,          // 0..1000
    val virtualizer: Int = 0,   // 0..1000
    val treble: Int = 0,        // millibels added to the top two bands
    val preset: String = "Flat"
) {
    fun toJson(): String = JSONObject()
        .put("enabled", enabled)
        .put("levels", JSONArray(levelsMb))
        .put("bass", bass)
        .put("virtualizer", virtualizer)
        .put("treble", treble)
        .put("preset", preset)
        .toString()

    companion object {
        fun fromJson(json: String?): EqState {
            if (json.isNullOrBlank()) return EqState()
            return try {
                val o = JSONObject(json)
                val arr = o.optJSONArray("levels")
                EqState(
                    enabled = o.optBoolean("enabled", false),
                    levelsMb = List(arr?.length() ?: 0) { arr!!.getInt(it) },
                    bass = o.optInt("bass", 0),
                    virtualizer = o.optInt("virtualizer", 0),
                    treble = o.optInt("treble", 0),
                    preset = o.optString("preset", "Flat")
                )
            } catch (_: Exception) {
                EqState()
            }
        }
    }
}
