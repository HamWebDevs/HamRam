package dev.hamweb.stayopen.data

import dev.hamweb.stayopen.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Codec for private preferences and explicitly exported setup backups. */
object PreferencesCodec {
    const val MAX_BACKUP_BYTES = 200_000
    fun encode(value: Preferences): String = JSONObject().put("format", "stayopen-setup").put("schema", 2)
        .put("appearanceVersion", 2).put("active", value.activeProfileId).put("theme", value.theme.name)
        .put("accent", value.accent.name).put("autoCare", value.autoCareEnabled)
        .put("ramClearApps", JSONArray(value.ramClearApps.sorted())).put("ramThreshold", value.ramThresholdPercent).put("ramCheck", value.ramCheckSeconds)
        .put("oneUi", value.oneUi.name).put("usage", value.usageEnabled)
        .put("profiles", JSONArray(value.profiles.map { p ->
            JSONObject().put("id", p.id).put("name", p.name).put("description", p.description)
                .put("packages", JSONArray(p.packages.sorted())).put("reviewed", JSONArray(p.reviewedPackages.sorted()))
                .put("interval", p.intervalSeconds).put("notes", p.notes)
        })).toString(2)

    fun decode(text: String, requireBackup: Boolean = false): Preferences {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "Backup is too large" }
        checkNesting(text)
        val root = JSONObject(text)
        if (requireBackup) require(root.optString("format") == "stayopen-setup" && root.optInt("schema") == 2) { "Not a supported StayOpen setup backup" }
        val profiles = root.optJSONArray("profiles")?.let { rows ->
            require(rows.length() in 1..20) { "Invalid profile count" }
            (0 until rows.length()).map { i ->
                val p = rows.getJSONObject(i)
                val id = p.getString("id"); val name = p.getString("name"); val notes = p.optString("notes")
                require(id.matches(Regex("[a-zA-Z0-9_-]{1,48}")) && name.isNotBlank() && name.length <= 40 && notes.length <= 2000)
                val packages = stringSet(p.optJSONArray("packages"))
                val reviewed = stringSet(p.optJSONArray("reviewed")) intersect packages
                Profile(id, name, p.optString("description").take(1000), packages, reviewed,
                    p.optInt("interval", 5).takeIf { it in PreferenceStore.INTERVALS } ?: 5, notes)
            }.also { require(it.map(Profile::id).distinct().size == it.size) { "Duplicate profile identifiers" } }
        } ?: if (requireBackup) error("No profiles in backup") else defaultProfiles()
        val active = root.optString("active", "everyday").takeIf { id -> profiles.any { it.id == id } } ?: profiles.first().id
        val savedTheme = enumValue(root.optString("theme"), ThemeMode.DARK)
        val theme = if (root.optInt("appearanceVersion", 0) < 2 && savedTheme == ThemeMode.SYSTEM) ThemeMode.DARK else savedTheme
        return Preferences(profiles, active, theme, enumValue(root.optString("oneUi"), OneUi.AUTO),
            root.optBoolean("usage", false), enumValue(root.optString("accent"), Accent.GREEN), root.optBoolean("autoCare", true),
            stringSet(root.optJSONArray("ramClearApps")),
            root.optInt("ramThreshold", 15).takeIf { it in RamClearPolicy.THRESHOLDS } ?: 15,
            root.optInt("ramCheck", 60).takeIf { it in RamClearPolicy.CHECK_INTERVALS } ?: 60)
    }
    private fun stringSet(a: JSONArray?): Set<String> {
        if (a == null) return emptySet()
        require(a.length() <= 200) { "Too many apps in a profile" }
        return (0 until a.length()).map { a.getString(it).also { name ->
            require(name.length <= 255 && name.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+"))) { "Invalid app identifier" }
        } }.toSet()
    }
    private inline fun <reified T : Enum<T>> enumValue(name: String, fallback: T): T = runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
    private fun checkNesting(text: String) {
        var quoted = false; var escaped = false; var depth = 0
        for (c in text) {
            if (quoted) { if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false }
            else when (c) { '"' -> quoted = true; '{', '[' -> { depth++; require(depth <= 20) { "Backup nesting is too deep" } }; '}', ']' -> depth-- }
        }
    }
}
