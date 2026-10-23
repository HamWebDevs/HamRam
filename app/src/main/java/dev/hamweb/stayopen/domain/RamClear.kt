package dev.hamweb.stayopen.domain

enum class BridgeAccess { CHECKING, NOT_INSTALLED, STOPPED, PERMISSION, DENIED, READY, UNSUPPORTED }

data class CachedProcess(val pid: Int, val uid: Int, val importance: Int, val packages: Set<String>)
data class RamCandidate(val packageName: String, val pids: List<Int>)
data class RamOutcome(val packageName: String, val requested: Int, val exited: Int, val error: String? = null)
data class RamRun(
    val timeMs: Long, val automatic: Boolean, val beforeBytes: Long, val afterBytes: Long,
    val outcomes: List<RamOutcome>, val durationMs: Long,
) {
    val exited: Int get() = outcomes.sumOf { it.exited }
    val requested: Int get() = outcomes.sumOf { it.requested }
}

data class RamState(
    val access: BridgeAccess = BridgeAccess.CHECKING,
    val detail: String = "Checking Shizuku availability…",
    val busy: Boolean = false,
    val automatic: Boolean = false,
    val screenPaused: Boolean = false,
    val candidates: List<RamCandidate>? = null,
    val scannedAtMs: Long? = null,
    val runs: List<RamRun> = emptyList(),
    val error: String? = null,
    val lastAvailableBytes: Long? = null,
)

/** Deliberately narrower than Android's kill-background operation: cached UIDs only. */
object RamClearPolicy {
    const val CACHED_IMPORTANCE = 400
    const val COOLDOWN_MS = 5 * 60_000L
    const val MAX_APPS_PER_RUN = 20
    val THRESHOLDS = listOf(10, 15, 20)
    val CHECK_INTERVALS = listOf(30, 60, 120)
    private val packagePattern = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+")

    fun validPackage(value: String) = value.length <= 255 && packagePattern.matches(value)

    fun candidates(processes: List<CachedProcess>, allowed: Set<String>, protected: Set<String>, userId: Int): List<RamCandidate> =
        processes.groupBy { it.uid }.values.mapNotNull { group ->
            val uid = group.first().uid
            val packages = group.flatMap { it.packages }.toSet()
            // Also guards shared UIDs and secondary processes: one active or excluded member blocks the UID.
            if (uid / 100_000 != userId || uid % 100_000 < 10_000 || packages.isEmpty() ||
                group.any { it.importance < CACHED_IMPORTANCE || it.pid <= 0 } ||
                packages.any { !validPackage(it) || it !in allowed || it in protected }) null
            else RamCandidate(packages.sorted().first(), group.map { it.pid }.distinct().sorted())
        }.sortedBy { it.packageName }.take(MAX_APPS_PER_RUN)

    fun underPressure(sample: MemorySample, percent: Int): Boolean = sample.totalBytes > 0 &&
        (sample.systemLowMemory || sample.availableBytes <= maxOf(sample.thresholdBytes, sample.totalBytes * percent / 100))

    fun shouldRun(consecutiveLowReadings: Int, nowUptimeMs: Long, lastRunUptimeMs: Long?): Boolean =
        consecutiveLowReadings >= 2 && (lastRunUptimeMs == null || nowUptimeMs - lastRunUptimeMs >= COOLDOWN_MS)
}
