package dev.hamweb.stayopen.data

import android.content.Context
import dev.hamweb.stayopen.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Small, explicit local schema. Disk reads and writes are called from the IO dispatcher. */
class PreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("stayopen_v1", Context.MODE_PRIVATE)

    fun load(): Preferences = runCatching { PreferencesCodec.decode(prefs.getString("preferences", "{}") ?: "{}") }.getOrElse { Preferences() }

    fun save(value: Preferences) {
        check(prefs.edit().putString("preferences", PreferencesCodec.encode(value)).commit()) { "Could not save preferences" }
    }

    fun loadEvents(): List<LowMemoryEvent> = runCatching {
        val a = JSONArray(prefs.getString("events", "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let {
            LowMemoryEvent(it.getLong("time"), it.getLong("available"), it.getLong("threshold"))
        } }.takeLast(100)
    }.getOrDefault(emptyList())

    fun saveEvents(events: List<LowMemoryEvent>) {
        check(prefs.edit().putString("events", JSONArray(events.takeLast(100).map {
            JSONObject().put("time", it.timeMs).put("available", it.availableBytes).put("threshold", it.thresholdBytes)
        }).toString()).commit()) { "Could not save low-memory history" }
    }

    fun loadCareRuns(): List<CareRun> = runCatching {
        val a = JSONArray(prefs.getString("care", "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let {
            CareRun(it.getLong("time"), CareReason.valueOf(it.getString("reason")), it.getLong("bytes"), it.getInt("icons"),
                it.getLong("before"), if (it.isNull("after")) null else it.getLong("after"), it.getLong("duration"))
        } }.takeLast(20)
    }.getOrDefault(emptyList())

    fun saveCareRuns(runs: List<CareRun>) {
        check(prefs.edit().putString("care", JSONArray(runs.takeLast(20).map {
            JSONObject().put("time", it.timeMs).put("reason", it.reason.name).put("bytes", it.clearedBytes)
                .put("icons", it.iconsDropped).put("before", it.beforeAvailableBytes)
                .put("after", it.afterAvailableBytes ?: JSONObject.NULL).put("duration", it.durationMs)
        }).toString()).commit()) { "Could not save care history" }
    }

    companion object { val INTERVALS = listOf(5, 10, 15, 30) }
}
