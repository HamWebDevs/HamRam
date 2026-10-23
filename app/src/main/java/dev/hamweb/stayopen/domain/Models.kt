package dev.hamweb.stayopen.domain

const val GIB = 1_073_741_824L

enum class Pressure(val label: String) { NORMAL("Normal"), ELEVATED("Elevated"), HIGH("High") }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class Accent(val label: String) { GREEN("Matrix green"), CYAN("Ice cyan"), AMBER("Amber terminal") }
enum class OneUi(val label: String) {
    AUTO("Automatic · based on Android"), V5("One UI 4 / 5"), V6("One UI 6"), V7("One UI 7"), V8("One UI 8 or newer")
}

data class MemorySample(
    val timeMs: Long,
    val totalBytes: Long,
    val availableBytes: Long,
    val thresholdBytes: Long,
    val systemLowMemory: Boolean,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0)
    val usedFraction: Float get() = if (totalBytes > 0) (usedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f) else 0f
    val pressure: Pressure get() = estimatePressure(this)
}

fun estimatePressure(sample: MemorySample): Pressure = when {
    sample.systemLowMemory || (sample.thresholdBytes > 0 && sample.availableBytes <= sample.thresholdBytes) -> Pressure.HIGH
    sample.availableBytes < maxOf(sample.thresholdBytes * 2, (sample.totalBytes * 0.15).toLong()) -> Pressure.ELEVATED
    else -> Pressure.NORMAL
}

data class Snapshot(
    val memory: MemorySample,
    val storageTotalBytes: Long,
    val storageAvailableBytes: Long,
    val powerSaver: Boolean,
    val lowRamDevice: Boolean,
    val thermalStatus: Int?,
    val appHeapBytes: Long,
    val uptimeMs: Long = 0,
    val batteryPercent: Int? = null,
    val batteryTemperatureC: Float? = null,
    val charging: Boolean? = null,
    val alwaysFinishActivities: Boolean? = null,
)

data class LowMemoryEvent(val timeMs: Long, val availableBytes: Long, val thresholdBytes: Long)
data class AppEntry(val packageName: String, val label: String)
data class AppRestriction(val packageName: String, val batteryExempt: Boolean?)
data class UsageEntry(val packageName: String, val label: String, val foregroundMs: Long, val lastUsedMs: Long)
data class OwnExit(val timeMs: Long, val reason: String, val description: String)

data class Profile(
    val id: String,
    val name: String,
    val description: String,
    val packages: Set<String> = emptySet(),
    val reviewedPackages: Set<String> = emptySet(),
    val intervalSeconds: Int = 5,
    val notes: String = "",
)

fun defaultProfiles() = listOf(
    Profile("everyday", "Everyday Use", "Prioritise messaging, navigation and the apps you rely on.", intervalSeconds = 5),
    Profile("gaming", "Gaming", "Keep your game and essential chat apps in your plan. Lower in-game textures if switching apps causes reloads.", intervalSeconds = 10),
    Profile("multitasking", "Maximum Multitasking", "Review background limits for a small set of essential apps. More exemptions can increase battery use.", intervalSeconds = 5),
    Profile("battery", "Battery Saver", "Keep only essential communication in your plan. Use power saving when longer battery life matters more than background activity.", intervalSeconds = 15),
    Profile("custom", "Custom", "Build your own app list, monitoring interval and setup notes.", intervalSeconds = 10),
)

data class Preferences(
    val profiles: List<Profile> = defaultProfiles(),
    val activeProfileId: String = "everyday",
    val theme: ThemeMode = ThemeMode.DARK,
    val oneUi: OneUi = OneUi.AUTO,
    val usageEnabled: Boolean = false,
    val accent: Accent = Accent.GREEN,
    val autoCareEnabled: Boolean = true,
) {
    val activeProfile: Profile get() = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.first()
}

enum class Evidence(val label: String) { MEASURED("Measured"), GUIDANCE("Possible explanation") }
enum class Action { APPS, STORAGE, BATTERY, SAMSUNG, DEVELOPER, NONE }
data class Finding(val id: String, val title: String, val detail: String, val evidence: Evidence, val priority: Int, val action: Action)

object DiagnosticsEngine {
    fun findings(snapshot: Snapshot, selected: List<AppRestriction>, samsung: Boolean): List<Finding> = buildList {
        if (snapshot.alwaysFinishActivities == true) add(Finding("activities", "Don't keep activities is enabled", "Android's activity-destruction setting is on. It can close an app's screen as soon as you leave it even when RAM is available. Open Developer options and switch Don't keep activities off. StayOpen reads this setting; it does not change it.", Evidence.MEASURED, 110, Action.DEVELOPER))
        when (snapshot.memory.pressure) {
            Pressure.HIGH -> add(Finding("memory", "Memory is under pressure", "Android reports low memory, or available RAM is at or below its reported low-memory threshold. Reduce the active workload and save work before switching apps. This does not identify which app, if any, was closed.", Evidence.MEASURED, 100, Action.APPS))
            Pressure.ELEVATED -> add(Finding("memory", "Less headroom for multitasking", "Available RAM is below StayOpen's conservative headroom estimate. Try fewer browser tabs or lower game textures. This estimate is not a reading of the kernel's memory-pressure system.", Evidence.MEASURED, 80, Action.APPS))
            Pressure.NORMAL -> Unit
        }
        if (snapshot.storageAvailableBytes < maxOf(2 * GIB, snapshot.storageTotalBytes / 10)) {
            add(Finding("storage", "Internal storage is running low", "Less than 10% or 2 GiB is available on the internal data volume. Review downloads and app caches in Android Settings. Storage pressure may make app updates and temporary writes fail.", Evidence.MEASURED, 95, Action.STORAGE))
        }
        if ((snapshot.thermalStatus ?: 0) >= 3) {
            add(Finding("thermal", "Android reports significant heat", "The device's thermal status is severe or higher. Pause demanding tasks and let the phone cool naturally. Heat may reduce performance; it does not prove an app was killed.", Evidence.MEASURED, 90, Action.NONE))
        }
        if (snapshot.powerSaver) {
            add(Finding("power", "Power saving is switched on", "Power saving can limit background work. Review it if timely app activity matters, accepting the battery trade-off.", Evidence.MEASURED, 75, Action.BATTERY))
        }
        val notExempt = selected.count { it.batteryExempt == false }
        if (notExempt > 0) add(Finding("exemption", "$notExempt preferred app(s) are not battery-exempt", "Android's Doze allowlist does not include these apps. That is a normal default, not proof of a problem. Review exemptions only for apps that need background work; this check cannot read their full Restricted / Optimised / Unrestricted setting.", Evidence.MEASURED, 60, Action.APPS))
        if (samsung) add(Finding("sleep", "Review Samsung's sleeping-app lists", "Samsung may defer background work for sleeping and deep sleeping apps. Review only apps you need and consider Never sleeping / Never auto sleeping apps. StayOpen cannot read these lists.", Evidence.GUIDANCE, 55, Action.SAMSUNG))
        if (snapshot.lowRamDevice) add(Finding("lowram", "Android flags this as a low-RAM device", "This system capability flag suggests a smaller multitasking budget. Keep fewer demanding apps active. The flag does not identify a faulty app.", Evidence.MEASURED, 65, Action.APPS))
        add(Finding("crash", "Check the app that keeps reloading", "A reload can also come from an app crash, an update, background limits or the app's own design. Another app's exit reason and RAM use are not available to this utility through normal public APIs. Check the affected app's updates and settings.", Evidence.GUIDANCE, 30, Action.APPS))
    }.sortedByDescending { it.priority }

    fun headline(findings: List<Finding>): String = when {
        findings.any { it.priority >= 90 } -> "Needs attention"
        findings.any { it.evidence == Evidence.MEASURED } -> "A few things to review"
        else -> "No current warning signs"
    }
}
