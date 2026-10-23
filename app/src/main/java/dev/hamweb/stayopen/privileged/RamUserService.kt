package dev.hamweb.stayopen.privileged

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Binder
import android.os.Process
import android.os.SystemClock
import androidx.annotation.Keep
import dev.hamweb.stayopen.domain.CachedProcess
import dev.hamweb.stayopen.domain.RamClearPolicy
import org.json.JSONArray
import org.json.JSONObject

/** Runs as shell through Shizuku, never inside the normal application sandbox. */
@Keep
class RamUserService @Keep constructor(private val context: Context) : IRamService.Stub() {
    private val ownerUid = context.applicationInfo.uid
    private val userId = ownerUid / 100_000
    private val activityManagerInterface = Class.forName("android.app.IActivityManager")
    private val manager = ActivityManager::class.java.getDeclaredMethod("getService").invoke(null)
    private val running = activityManagerInterface.getMethod("getRunningAppProcesses")
    private val kill = activityManagerInterface.getMethod("killBackgroundProcesses", String::class.java, Int::class.javaPrimitiveType)

    private fun checkCaller() {
        check(Binder.getCallingUid() == ownerUid || Binder.getCallingUid() == Process.myUid()) { "Wrong caller" }
        check(Process.myUid() == 2000 || Process.myUid() == 0) { "Shizuku shell/root privilege is required" }
    }

    @Suppress("UNCHECKED_CAST")
    private fun processes(): List<CachedProcess> =
        (running.invoke(manager) as? List<ActivityManager.RunningAppProcessInfo>).orEmpty().map {
            // getPackagesForUid catches shared packages absent from the current process's pkgList.
            val packages = it.pkgList.orEmpty().toSet() + context.packageManager.getPackagesForUid(it.uid).orEmpty()
            CachedProcess(it.pid, it.uid, it.importance, packages)
        }

    private fun allowed(names: Array<out String>): Set<String> {
        require(names.size <= 200 && names.all(RamClearPolicy::validPackage)) { "Invalid app list" }
        return names.toSet().filter { name ->
            if (name == context.packageName || name == "moe.shizuku.privileged.api") false else runCatching {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getApplicationInfo(name, 0)
                info.uid / 100_000 == userId && info.uid != ownerUid &&
                    info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            }.getOrDefault(false)
        }.toSet()
    }

    @Synchronized
    override fun scan(allowed: Array<out String>, protectedApps: Array<out String>): String {
        checkCaller()
        require(protectedApps.size <= 4_010 && protectedApps.all(RamClearPolicy::validPackage))
        val token = Binder.clearCallingIdentity()
        try {
            val candidates = RamClearPolicy.candidates(processes(), allowed(allowed), protectedApps.toSet() + context.packageName, userId)
            return JSONObject().put("uid", Process.myUid()).put("candidates", JSONArray(candidates.map {
                JSONObject().put("package", it.packageName).put("pids", JSONArray(it.pids))
            })).toString()
        } finally { Binder.restoreCallingIdentity(token) }
    }

    @Synchronized
    override fun clear(allowed: Array<out String>, protectedApps: Array<out String>): String {
        checkCaller()
        require(protectedApps.size <= 4_010 && protectedApps.all(RamClearPolicy::validPackage))
        val token = Binder.clearCallingIdentity()
        try {
            val started = SystemClock.elapsedRealtime()
            val accepted = allowed(allowed)
            val excluded = protectedApps.toSet() + context.packageName
            val candidates = RamClearPolicy.candidates(processes(), accepted, excluded, userId)
            val requested = linkedMapOf<String, List<Int>>()
            val errors = mutableMapOf<String, String>()
            for (candidate in candidates) {
                // Recheck immediately before each call; never act on a stale preview.
                val fresh = RamClearPolicy.candidates(processes(), accepted, excluded, userId)
                    .firstOrNull { it.packageName == candidate.packageName } ?: continue
                requested[candidate.packageName] = fresh.pids
                try { kill.invoke(manager, candidate.packageName, userId) }
                catch (_: Exception) { errors[candidate.packageName] = "Android refused this background-clear request" }
            }
            // Process exit is asynchronous. Observe PIDs, rather than count accepted calls as kills.
            var alive = processes().map { it.pid }.toSet()
            var attempts = 0
            while (requested.values.flatten().any { it in alive } && attempts++ < 5) {
                Thread.sleep(200)
                alive = processes().map { it.pid }.toSet()
            }
            return JSONObject().put("uid", Process.myUid()).put("duration", SystemClock.elapsedRealtime() - started)
                .put("outcomes", JSONArray(requested.map { (name, pids) ->
                    JSONObject().put("package", name).put("requested", pids.size)
                        .put("exited", if (name in errors) 0 else pids.count { it !in alive })
                        .put("error", errors[name] ?: JSONObject.NULL)
                })).toString()
        } finally { Binder.restoreCallingIdentity(token) }
    }

    override fun destroy() { kotlin.system.exitProcess(0) }
}
