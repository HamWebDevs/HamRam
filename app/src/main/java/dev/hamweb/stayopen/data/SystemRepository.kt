package dev.hamweb.stayopen.data

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.ApplicationExitInfo
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import dev.hamweb.stayopen.domain.*
import java.io.File

class SystemRepository(private val context: Context) {
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val packageManager = context.packageManager

    fun snapshot(): Snapshot {
        val info = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val storage = StatFs(Environment.getDataDirectory().absolutePath)
        val runtime = Runtime.getRuntime()
        return Snapshot(
            MemorySample(System.currentTimeMillis(), info.totalMem, info.availMem, info.threshold, info.lowMemory),
            storage.totalBytes, storage.availableBytes, powerManager.isPowerSaveMode, activityManager.isLowRamDevice,
            if (Build.VERSION.SDK_INT >= 29) powerManager.currentThermalStatus else null,
            runtime.totalMemory() - runtime.freeMemory(),
        )
    }

    /** Only launchable apps, using scoped package visibility instead of QUERY_ALL_PACKAGES. */
    fun installedApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val results = if (Build.VERSION.SDK_INT >= 33)
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        else @Suppress("DEPRECATION") packageManager.queryIntentActivities(intent, 0)
        return results.map { AppEntry(it.activityInfo.packageName, it.loadLabel(packageManager).toString()) }
            .filter { it.packageName != context.packageName }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    fun restrictions(packages: Set<String>): List<AppRestriction> = packages.map { name ->
        AppRestriction(name, runCatching {
            if (Build.VERSION.SDK_INT >= 33) packageManager.getApplicationInfo(name, PackageManager.ApplicationInfoFlags.of(0))
            else @Suppress("DEPRECATION") packageManager.getApplicationInfo(name, 0)
            powerManager.isIgnoringBatteryOptimizations(name)
        }.getOrNull())
    }

    fun hasUsagePermission(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usage(apps: List<AppEntry>): List<UsageEntry> {
        if (!hasUsagePermission()) return emptyList()
        val end = System.currentTimeMillis()
        val manager = context.getSystemService(UsageStatsManager::class.java)
        // Android aggregates into buckets; the UI and report explicitly describe this approximation.
        val labels = apps.associate { it.packageName to it.label }
        return manager.queryAndAggregateUsageStats(end - 86_400_000L, end).values
            .filter { it.totalTimeInForeground > 0 && labels.containsKey(it.packageName) }
            .map { UsageEntry(it.packageName, labels.getValue(it.packageName), it.totalTimeInForeground, it.lastTimeUsed) }
            .sortedByDescending { it.foregroundMs }.take(20)
    }

    fun ownExits(): List<OwnExit> {
        if (Build.VERSION.SDK_INT < 30) return emptyList()
        // Without privileged permissions Android only exposes this app's own historical exits.
        return activityManager.getHistoricalProcessExitReasons(context.packageName, 0, 6).map {
            val reason = when (it.reason) {
                ApplicationExitInfo.REASON_LOW_MEMORY -> "System low memory"
                ApplicationExitInfo.REASON_CRASH -> "Java / Kotlin crash"
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
                ApplicationExitInfo.REASON_ANR -> "Not responding"
                ApplicationExitInfo.REASON_USER_REQUESTED -> "User / system request"
                ApplicationExitInfo.REASON_SIGNALED -> "Signal"
                ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Excessive resource use"
                else -> "Android reason ${it.reason}"
            }
            OwnExit(it.timestamp, reason, it.description.orEmpty().take(250))
        }
    }

    /** Never traverses user storage. No history, preferences or exported reports are touched. */
    fun clearOwnTemporaryFiles(): Long {
        val root = context.cacheDir.canonicalFile
        var deleted = 0L
        fun remove(file: File) {
            val canonical = file.canonicalFile
            if (!canonical.path.startsWith(root.path + File.separator)) return
            if (file.isDirectory) file.listFiles()?.forEach(::remove)
            val size = if (file.isFile) file.length() else 0L
            if (file.delete()) deleted += size
        }
        root.listFiles()?.forEach(::remove)
        return deleted
    }
}
