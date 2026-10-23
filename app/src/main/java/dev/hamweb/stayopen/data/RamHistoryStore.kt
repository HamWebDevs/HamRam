package dev.hamweb.stayopen.data

import android.content.Context
import dev.hamweb.stayopen.domain.*
import org.json.JSONArray
import org.json.JSONObject

class RamHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("stayopen_v1", Context.MODE_PRIVATE)
    fun load(): List<RamRun> = runCatching {
        val rows = JSONArray(prefs.getString("ramRuns", "[]"))
        (0 until rows.length()).map { i -> rows.getJSONObject(i).let { row ->
            RamRun(row.getLong("time"), row.getBoolean("auto"), row.getLong("before"), row.getLong("after"),
                outcomes(row.getJSONArray("outcomes")), row.getLong("duration"))
        } }.takeLast(20)
    }.getOrDefault(emptyList())

    fun save(runs: List<RamRun>) {
        val rows = JSONArray(runs.takeLast(20).map { run ->
            JSONObject().put("time", run.timeMs).put("auto", run.automatic).put("before", run.beforeBytes)
                .put("after", run.afterBytes).put("duration", run.durationMs)
                .put("outcomes", JSONArray(run.outcomes.map {
                    JSONObject().put("package", it.packageName).put("requested", it.requested)
                        .put("exited", it.exited).put("error", it.error ?: JSONObject.NULL)
                }))
        })
        check(prefs.edit().putString("ramRuns", rows.toString()).commit()) { "Couldn't save RAM clearing history" }
    }

    companion object {
        fun outcomes(rows: JSONArray): List<RamOutcome> = (0 until rows.length()).map { i -> rows.getJSONObject(i).let {
            RamOutcome(it.getString("package"), it.getInt("requested"), it.getInt("exited"),
                if (it.isNull("error")) null else it.getString("error"))
        } }
    }
}
