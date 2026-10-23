package dev.hamweb.stayopen.data

import dev.hamweb.stayopen.domain.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {
    private fun bytes(value: Long) = String.format(Locale.UK, "%.2f GiB (%d bytes)", value.toDouble() / GIB, value)
    private fun date(value: Long) = DateFormat.getDateTimeInstance().format(Date(value))

    fun create(
        version: String, device: DeviceInfo, snapshot: Snapshot?, preferences: Preferences,
        apps: List<AppEntry>, restrictions: List<AppRestriction>, samples: List<MemorySample>,
        events: List<LowMemoryEvent>, usage: List<UsageEntry>, exits: List<OwnExit>,
    ): String = buildString {
        appendLine("STAYOPEN · DIAGNOSTIC REPORT")
        appendLine("Generated: ${date(System.currentTimeMillis())}")
        appendLine("App version: $version")
        appendLine("Device: ${device.friendlyModel} (${device.model})")
        appendLine("Android: ${device.androidVersion}, API ${device.sdk}")
        appendLine("One UI guidance selection: ${preferences.oneUi.label} (automatic is an Android-based menu estimate, not a detected One UI version)")
        appendLine("No account, serial number, IMEI or phone number is included.")
        appendLine()
        if (snapshot == null) appendLine("No system snapshot has been collected.") else {
            appendLine("MEASURED SNAPSHOT · ${date(snapshot.memory.timeMs)}")
            appendLine("Total physical RAM exposed by Android: ${bytes(snapshot.memory.totalBytes)}")
            appendLine("Available RAM: ${bytes(snapshot.memory.availableBytes)}")
            appendLine("Android low-memory threshold: ${bytes(snapshot.memory.thresholdBytes)}")
            appendLine("Android lowMemory flag: ${snapshot.memory.systemLowMemory}")
            appendLine("Estimated pressure: ${snapshot.memory.pressure.label}")
            appendLine("Internal data volume: ${bytes(snapshot.storageTotalBytes)} total; ${bytes(snapshot.storageAvailableBytes)} available")
            appendLine("Power saving: ${snapshot.powerSaver}; low-RAM device flag: ${snapshot.lowRamDevice}")
            appendLine("Thermal status: ${snapshot.thermalStatus ?: "not available"} (Android: 0 none, 1 light, 2 moderate, 3 severe, 4 critical, 5 emergency, 6 shutdown)")
            appendLine("StayOpen managed heap in use: ${bytes(snapshot.appHeapBytes)} (not total app RAM)")
            appendLine()
            appendLine("HEALTH REPORT · ranked recommendations, not a diagnosis")
            val findings = DiagnosticsEngine.findings(snapshot, restrictions, device.isSamsung)
            appendLine(DiagnosticsEngine.headline(findings))
            findings.forEachIndexed { i, f -> appendLine("${i + 1}. [${f.evidence.label}] ${f.title}\n${f.detail}") }
        }
        appendLine()
        appendLine("PROFILE · ${preferences.activeProfile.name}")
        appendLine(preferences.activeProfile.description)
        appendLine("Sampling interval: ${preferences.activeProfile.intervalSeconds} seconds; only while the activity is visible and monitoring is on.")
        val labels = apps.associate { it.packageName to it.label }
        restrictions.forEach { r ->
            appendLine("${labels[r.packageName] ?: r.packageName} (${r.packageName}): Doze exemption=${r.batteryExempt ?: "unknown"}; setup self-reviewed=${r.packageName in preferences.activeProfile.reviewedPackages}")
        }
        appendLine("Profile notes: ${preferences.activeProfile.notes.ifBlank { "none" }}")
        appendLine("Samsung sleeping lists and full per-app battery restriction settings cannot be verified by StayOpen.")
        appendLine()
        appendLine("FOREGROUND RAM SAMPLES · ${samples.size}, bounded to 360 samples")
        appendLine("time,total_bytes,available_bytes,threshold_bytes,system_low_memory,estimated_pressure")
        samples.forEach { appendLine("${date(it.timeMs)},${it.totalBytes},${it.availableBytes},${it.thresholdBytes},${it.systemLowMemory},${it.pressure.name}") }
        appendLine()
        appendLine("LOW-MEMORY OBSERVATIONS · ${events.size}, bounded to 100, gaps are unmonitored")
        events.forEach { appendLine("${date(it.timeMs)} · ${bytes(it.availableBytes)} available · threshold ${bytes(it.thresholdBytes)}") }
        appendLine()
        appendLine("OPTIONAL APP USAGE · ${if (preferences.usageEnabled) "enabled" else "disabled"}")
        appendLine("Android aggregated foreground use queried for the last 24 hours. System bucket boundaries may include time outside that window. Usage is not RAM use or evidence of an app closure.")
        usage.forEach { appendLine("${it.label} (${it.packageName}): ${it.foregroundMs / 60_000} foreground minutes; last used ${date(it.lastUsedMs)}") }
        appendLine()
        appendLine("STAYOPEN'S OWN EXIT RECORDS · other apps' exits are inaccessible")
        exits.forEach { appendLine("${date(it.timeMs)} · ${it.reason} · ${it.description}") }
        appendLine()
        appendLine("LIMITS AND METHOD")
        appendLine("ActivityManager.getMemoryInfo, StatFs, PowerManager, optional UsageStatsManager and this app's ApplicationExitInfo. Used RAM = total - available; it includes useful caches and does not mean wasted memory.")
        appendLine("Estimated high pressure: Android lowMemory flag or available <= reported threshold. Elevated: available < max(2 × threshold, 15% of total). This is a heuristic, not kernel PSI or proof an app was killed.")
        appendLine("StayOpen does not keep other processes alive, override the low-memory killer, read other apps' private memory, change protected settings, clear others' caches or force-stop apps. All monitoring stops when StayOpen is not visible.")
        appendLine("Privacy: reports can contain app names, usage and your notes. Share only with someone you trust. No upload happens automatically.")
        appendLine()
        appendLine("REFERENCE LINKS (reading only; the application itself operates offline)")
        appendLine("https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo")
        appendLine("https://developer.android.com/reference/android/os/PowerManager")
        appendLine("https://www.samsung.com/us/support/answer/ANS10003442/")
    }
}
