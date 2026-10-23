package dev.hamweb.stayopen.domain

enum class CareReason(val label: String) { MANUAL("Manual care"), START("Automatic · app opened"), INTERVAL("Automatic · 15 min interval"), PRESSURE("Automatic · high memory pressure") }

data class CareRun(
    val timeMs: Long,
    val reason: CareReason,
    val clearedBytes: Long,
    val iconsDropped: Int,
    val beforeAvailableBytes: Long,
    val afterAvailableBytes: Long?,
    val durationMs: Long,
)

object AutoCarePolicy {
    const val INTERVAL_MS = 15 * 60_000L
    const val PRESSURE_COOLDOWN_MS = 2 * 60_000L

    /** Evaluated by the existing visible sampling loop; no background timer or service. */
    fun reason(enabled: Boolean, uptimeMs: Long, lastRunUptimeMs: Long?, previous: Pressure?, current: Pressure): CareReason? {
        if (!enabled) return null
        if (lastRunUptimeMs == null) return CareReason.START
        val elapsed = (uptimeMs - lastRunUptimeMs).coerceAtLeast(0)
        if (elapsed >= PRESSURE_COOLDOWN_MS && current == Pressure.HIGH && previous != Pressure.HIGH) return CareReason.PRESSURE
        if (elapsed >= INTERVAL_MS) return CareReason.INTERVAL
        return null
    }

    fun sampleInterval(configuredSeconds: Int, enabled: Boolean, pressure: Pressure?): Int =
        if (enabled && pressure == Pressure.HIGH) maxOf(configuredSeconds, 10) else configuredSeconds
}

data class MemorySummary(val minimumAvailable: Long, val maximumAvailable: Long, val averageAvailable: Long, val count: Int)

fun summariseMemory(samples: List<MemorySample>): MemorySummary? {
    if (samples.isEmpty()) return null
    return MemorySummary(samples.minOf { it.availableBytes }, samples.maxOf { it.availableBytes },
        samples.map { it.availableBytes.toDouble() }.average().toLong(), samples.size)
}
