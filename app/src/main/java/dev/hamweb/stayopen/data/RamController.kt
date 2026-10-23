package dev.hamweb.stayopen.data

import android.app.ActivityManager
import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import android.os.Build
import dev.hamweb.stayopen.domain.*
import dev.hamweb.stayopen.service.RamMonitorService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject

/** One process-wide coordinator prevents overlapping manual and automatic clears. */
class RamController private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val history = RamHistoryStore(context)
    private val _state = MutableStateFlow(RamState())
    val state = _state.asStateFlow()
    private var preferences = Preferences()
    private var visible = false
    private val operation = Mutex()
    private var automaticJob: Job? = null
    private var lastRunUptimeMs: Long? = null
    private val loaded = CompletableDeferred<Unit>()
    private val bridge = ShizukuBridge(context) { access, detail ->
        _state.update { it.copy(access = access, detail = detail) }
        if (access != BridgeAccess.READY && state.value.automatic) stopAutomatic()
    }

    init {
        scope.launch {
            val runs = withContext(Dispatchers.IO) { history.load() }
            runs.lastOrNull()?.let {
                val age = (System.currentTimeMillis() - it.timeMs).coerceAtLeast(0)
                lastRunUptimeMs = SystemClock.elapsedRealtime() - age.coerceAtMost(RamClearPolicy.COOLDOWN_MS)
            }
            _state.update { it.copy(runs = runs) }; loaded.complete(Unit)
        }
    }

    fun configure(value: Preferences) {
        if (value.ramClearApps != preferences.ramClearApps || value.profiles != preferences.profiles)
            _state.update { it.copy(candidates = null, scannedAtMs = null) }
        preferences = value
        if (value.ramClearApps.isEmpty() && state.value.automatic) stopAutomatic()
    }

    fun setVisible(value: Boolean) {
        visible = value
        if (value) bridge.refresh()
        else if (!state.value.automatic && !state.value.busy) bridge.release()
    }
    fun refreshAccess() { bridge.refresh() }
    fun requestPermission() { bridge.requestPermission() }
    fun dismissError() { _state.update { it.copy(error = null) } }

    private fun memory(): MemorySample {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        return MemorySample(System.currentTimeMillis(), info.totalMem, info.availMem, info.threshold, info.lowMemory)
    }

    private fun exclusions(prefs: Preferences): Set<String> = buildSet {
        // Every profile is honoured, even when a different profile is currently selected.
        addAll(prefs.profiles.flatMap { it.packages })
        add(context.packageName); add("moe.shizuku.privileged.api")
        runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) }
            .getOrNull()?.substringBefore('/')?.takeIf(RamClearPolicy::validPackage)?.let(::add)
        runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }
            .getOrNull()?.takeIf(RamClearPolicy::validPackage)?.let(::add)
        @Suppress("DEPRECATION")
        runCatching { context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0) }
            .getOrNull()?.activityInfo?.packageName?.takeIf(RamClearPolicy::validPackage)?.let(::add)
    }

    fun scan() { scope.launch {
        loaded.await()
        if (!operation.tryLock()) return@launch
        _state.update { it.copy(busy = true, error = null) }
        try {
            val remote = bridge.service()
            val prefs = preferences
            val candidates = withContext(Dispatchers.IO) {
                val rows = JSONObject(remote.scan(prefs.ramClearApps.toTypedArray(), exclusions(prefs).toTypedArray())).getJSONArray("candidates")
                (0 until rows.length()).map { i -> rows.getJSONObject(i).let { row ->
                    val pids = row.getJSONArray("pids")
                    RamCandidate(row.getString("package"), (0 until pids.length()).map(pids::getInt))
                } }
            }
            _state.update { it.copy(candidates = candidates, scannedAtMs = System.currentTimeMillis()) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { fail("Couldn't inspect cached processes. Check Shizuku access, then retry.") }
        finally { finishOperation() }
    } }

    fun clearNow() { scope.launch { clear(automatic = false) } }

    private suspend fun clear(automatic: Boolean) {
        loaded.await()
        if (!operation.tryLock()) return
        _state.update { it.copy(busy = true, error = null) }
        try {
            check(preferences.ramClearApps.isNotEmpty()) { "Choose apps to clear first" }
            val remote = bridge.service()
            // Once issued, finish recording a real result even if the Stop button cancels the monitor.
            withContext(NonCancellable) {
                val prefs = preferences
                val run = withContext(Dispatchers.IO) {
                    val started = SystemClock.elapsedRealtime()
                    val before = memory()
                    val result = JSONObject(remote.clear(prefs.ramClearApps.toTypedArray(), exclusions(prefs).toTypedArray()))
                    RamRun(System.currentTimeMillis(), automatic, before.availableBytes, memory().availableBytes,
                        RamHistoryStore.outcomes(result.getJSONArray("outcomes")), SystemClock.elapsedRealtime() - started)
                }
                lastRunUptimeMs = SystemClock.elapsedRealtime()
                val runs = (state.value.runs + run).takeLast(20)
                _state.update { it.copy(runs = runs, candidates = null, scannedAtMs = null, lastAvailableBytes = run.afterBytes) }
                withContext(Dispatchers.IO) { history.save(runs) }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            fail("RAM clearing couldn't finish. Check Shizuku and its StayOpen permission. No force-stop or file deletion was requested.")
            if (automatic) stopAutomatic()
        } finally { finishOperation() }
    }

    private fun finishOperation() {
        _state.update { it.copy(busy = false) }; operation.unlock()
        if (!visible && !state.value.automatic) bridge.release()
    }

    private fun fail(message: String) { bridge.refresh(); _state.update { it.copy(error = message) } }

    /** Only called by a foreground service started through a visible user action. Never auto-restarts. */
    fun beginAutomaticSession() {
        if (automaticJob?.isActive == true) return
        if (bridge.refresh() != BridgeAccess.READY || preferences.ramClearApps.isEmpty()) {
            fail("Choose apps and grant Shizuku access before starting Auto clear.")
            context.stopService(Intent(context, RamMonitorService::class.java)); return
        }
        _state.update { it.copy(automatic = true, error = null) }
        automaticJob = scope.launch {
            loaded.await()
            var consecutive = 0
            try { while (isActive) {
                if (bridge.refresh() != BridgeAccess.READY) break
                val interactive = context.getSystemService(PowerManager::class.java).isInteractive
                _state.update { it.copy(screenPaused = !interactive) }
                if (interactive) {
                    val reading = withContext(Dispatchers.IO) { memory() }
                    _state.update { it.copy(lastAvailableBytes = reading.availableBytes) }
                    consecutive = if (RamClearPolicy.underPressure(reading, preferences.ramThresholdPercent)) (consecutive + 1).coerceAtMost(2) else 0
                    if (RamClearPolicy.shouldRun(consecutive, SystemClock.elapsedRealtime(), lastRunUptimeMs)) {
                        clear(automatic = true); consecutive = 0
                    }
                } else consecutive = 0
                // No wake lock or alarms. While asleep, this timer does not wake the device.
                delay(preferences.ramCheckSeconds * 1_000L)
            } } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { fail("Auto clear stopped because a system reading failed. You can restart it from Optimise."); stopAutomatic() }
        }
    }

    fun startAutomatic(): String? {
        if (bridge.refresh() != BridgeAccess.READY) return "Start Shizuku and grant StayOpen access first."
        if (preferences.ramClearApps.isEmpty()) return "Choose apps that may be cleared first."
        if ((Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) ||
            !NotificationManagerCompat.from(context).areNotificationsEnabled() ||
            context.getSystemService(NotificationManager::class.java).getNotificationChannel(RamMonitorService.CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE)
            return "Allow StayOpen's Automatic RAM clearing notification in Android Settings so its Stop control is visible."
        return runCatching { ContextCompat.startForegroundService(context, Intent(context, RamMonitorService::class.java)); null }
            .getOrElse { "Android couldn't start Auto clear. Keep StayOpen visible and try again." }
    }

    fun stopAutomatic() { endAutomaticSession(); context.stopService(Intent(context, RamMonitorService::class.java)) }
    fun endAutomaticSession() {
        automaticJob?.cancel(); automaticJob = null
        _state.update { it.copy(automatic = false, screenPaused = false) }
        if (!visible && !state.value.busy) bridge.release()
    }

    fun clearHistory() { scope.launch {
        loaded.await()
        if (!operation.tryLock()) { _state.update { it.copy(error = "Wait for the current RAM operation before clearing its history.") }; return@launch }
        try { withContext(Dispatchers.IO) { history.save(emptyList()) }; _state.update { it.copy(runs = emptyList(), candidates = null, scannedAtMs = null) } }
        finally { operation.unlock() }
    } }

    companion object {
        @Volatile private var instance: RamController? = null
        fun get(context: Context): RamController = instance ?: synchronized(this) {
            instance ?: RamController(context.applicationContext).also { instance = it }
        }
    }
}
