package dev.hamweb.stayopen.data

import android.content.Context
import dev.hamweb.stayopen.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Small, explicit local schema. Disk reads and writes are called from the IO dispatcher. */
class PreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("stayopen_v1", Context.MODE_PRIVATE)

    fun load(): Preferences = runCatching {
        val root = JSONObject(prefs.getString("preferences", "{}") ?: "{}")
        val profiles = root.optJSONArray("profiles")?.let { rows ->
            (0 until rows.length()).map { i ->
                val p = rows.getJSONObject(i)
                Profile(p.getString("id"), p.getString("name"), p.optString("description"),
                    stringSet(p.optJSONArray("packages")), stringSet(p.optJSONArray("reviewed")),
                    p.optInt("interval", 5).takeIf { it in INTERVALS } ?: 5, p.optString("notes"))
            }
        }?.takeIf { it.isNotEmpty() } ?: defaultProfiles()
        Preferences(profiles, root.optString("active", "everyday"),
            enumValue(root.optString("theme"), ThemeMode.SYSTEM),
            enumValue(root.optString("oneUi"), OneUi.AUTO), root.optBoolean("usage", false))
    }.getOrElse { Preferences() }

    fun save(value: Preferences) {
        val root = JSONObject().put("active", value.activeProfileId).put("theme", value.theme.name)
            .put("oneUi", value.oneUi.name).put("usage", value.usageEnabled)
            .put("profiles", JSONArray(value.profiles.map { p ->
                JSONObject().put("id", p.id).put("name", p.name).put("description", p.description)
                    .put("packages", JSONArray(p.packages.sorted())).put("reviewed", JSONArray(p.reviewedPackages.sorted()))
                    .put("interval", p.intervalSeconds).put("notes", p.notes)
            }))
        check(prefs.edit().putString("preferences", root.toString()).commit()) { "Could not save preferences" }
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

    companion object {
        val INTERVALS = listOf(5, 10, 15, 30)
        private fun stringSet(a: JSONArray?): Set<String> = if (a == null) emptySet() else
            (0 until a.length()).map { a.getString(it) }.toSet()
        private inline fun <reified T : Enum<T>> enumValue(name: String, fallback: T): T =
            runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
    }
}
