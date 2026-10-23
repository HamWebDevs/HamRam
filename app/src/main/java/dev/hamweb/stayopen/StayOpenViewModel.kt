package dev.hamweb.stayopen

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.hamweb.stayopen.data.*
import dev.hamweb.stayopen.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class StayOpenState(
    val ready: Boolean = false,
    val preferences: Preferences = Preferences(),
    val snapshot: Snapshot? = null,
    val samples: List<MemorySample> = emptyList(),
    val events: List<LowMemoryEvent> = emptyList(),
    val apps: List<AppEntry> = emptyList(),
    val restrictions: List<AppRestriction> = emptyList(),
    val usage: List<UsageEntry> = emptyList(),
    val usagePermission: Boolean = false,
    val exits: List<OwnExit> = emptyList(),
    val monitoring: Boolean = true,
    val refreshing: Boolean = false,
    val message: String? = null,
    val samplingError: String? = null,
)

class StayOpenViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SystemRepository(application)
    private val store = PreferenceStore(application)
    val device = DeviceInfo()
    private val _state = MutableStateFlow(StayOpenState())
    val state = _state.asStateFlow()
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var previousLow = false
    private var visible = false

    init {
        viewModelScope.launch {
            for (write in writes) {
                try { withContext(Dispatchers.IO) { write() } }
                catch (_: Exception) { message("A preference or history change could not be saved. Please try again.") }
            }
        }
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { store.load() to store.loadEvents() }
            _state.update { it.copy(ready = true, preferences = loaded.first, events = loaded.second) }
        }
    }

    /** Invoked only by repeatOnLifecycle(STARTED). Cancellation halts every sampling delay/read. */
    suspend fun monitorWhileVisible() {
        state.first { it.ready }
        try {
            visible = true
            refreshContext()
            while (currentCoroutineContext().isActive) {
                if (!state.value.monitoring) {
                    previousLow = false
                    state.first { it.monitoring }
                }
                takeSample()
                delay(state.value.preferences.activeProfile.intervalSeconds * 1_000L)
            }
        } finally {
            visible = false
            previousLow = false // A return to the app starts a new, explicitly bounded observation.
        }
    }

    private suspend fun takeSample() {
        try {
            val snapshot = withContext(Dispatchers.IO) { repository.snapshot() }
            if (!state.value.monitoring || !visible) return
            _state.update { it.copy(snapshot = snapshot, samples = (it.samples + snapshot.memory).takeLast(360), samplingError = null) }
            // Persist only a transition into Android's low-memory flag, not heuristic pressure warnings.
            if (snapshot.memory.systemLowMemory && !previousLow) {
                val event = LowMemoryEvent(snapshot.memory.timeMs, snapshot.memory.availableBytes, snapshot.memory.thresholdBytes)
                val events = (state.value.events + event).takeLast(100)
                _state.update { it.copy(events = events) }
                writes.send { store.saveEvents(events) }
            }
            previousLow = snapshot.memory.systemLowMemory
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _state.update { it.copy(samplingError = "Couldn't read system statistics. Last reading is retained; try Refresh.") } }
    }

    private suspend fun refreshContext() {
        _state.update { it.copy(refreshing = true) }
        try {
            val prefs = state.value.preferences
            val result = withContext(Dispatchers.IO) {
                val apps = repository.installedApps()
                val permission = repository.hasUsagePermission()
                ContextData(apps, repository.restrictions(prefs.activeProfile.packages), permission,
                    if (prefs.usageEnabled && permission) repository.usage(apps) else emptyList(), repository.ownExits())
            }
            _state.update { current -> current.copy(apps = result.apps,
                restrictions = if (current.preferences.activeProfile.packages == prefs.activeProfile.packages) result.restrictions else current.restrictions,
                usagePermission = result.permission,
                usage = if (current.preferences.usageEnabled) result.usage else emptyList(), exits = result.exits) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message("Some app or usage details aren't available on this device. Memory monitoring can still work.") }
        finally { _state.update { it.copy(refreshing = false) } }
    }

    fun refresh() { if (!state.value.refreshing) viewModelScope.launch { refreshContext(); if (state.value.monitoring) takeSample() } }
    fun toggleMonitoring() { _state.update { it.copy(monitoring = !it.monitoring) } }
    fun message(text: String?) { _state.update { it.copy(message = text) } }

    private fun preferences(transform: (Preferences) -> Preferences) {
        val value = transform(state.value.preferences)
        _state.update { it.copy(preferences = value) }
        writes.trySend { store.save(value) }
    }
    private fun editProfile(transform: (Profile) -> Profile) = preferences { prefs ->
        prefs.copy(profiles = prefs.profiles.map { if (it.id == prefs.activeProfileId) transform(it) else it })
    }
    fun selectProfile(id: String) {
        if (state.value.preferences.profiles.none { it.id == id }) return
        preferences { it.copy(activeProfileId = id) }
        _state.update { it.copy(restrictions = emptyList()) }
        refreshRestrictions()
        refresh()
    }
    fun toggleApp(packageName: String) {
        editProfile { p -> p.copy(packages = if (packageName in p.packages) p.packages - packageName else p.packages + packageName) }
        refreshRestrictions()
    }
    private fun refreshRestrictions() {
        viewModelScope.launch {
            val packages = state.value.preferences.activeProfile.packages
            val checked = withContext(Dispatchers.IO) { repository.restrictions(packages) }
            _state.update { if (it.preferences.activeProfile.packages == packages) it.copy(restrictions = checked) else it }
        }
    }
    fun markReviewed(packageName: String, reviewed: Boolean) = editProfile { p ->
        p.copy(reviewedPackages = if (reviewed) p.reviewedPackages + packageName else p.reviewedPackages - packageName)
    }
    fun setInterval(seconds: Int) { if (seconds in PreferenceStore.INTERVALS) editProfile { it.copy(intervalSeconds = seconds) } }
    fun saveProfileDetails(name: String, notes: String) = editProfile { it.copy(name = name.trim().take(40).ifBlank { it.name }, notes = notes.take(2000)) }
    fun setTheme(mode: ThemeMode) = preferences { it.copy(theme = mode) }
    fun setOneUi(mode: OneUi) = preferences { it.copy(oneUi = mode) }
    fun setUsage(enabled: Boolean) { preferences { it.copy(usageEnabled = enabled) }; if (!enabled) _state.update { it.copy(usage = emptyList()) } else refresh() }

    fun clearHistory() {
        _state.update { it.copy(samples = emptyList(), events = emptyList()) }
        previousLow = false
        writes.trySend { store.saveEvents(emptyList()) }
        message("RAM graph and saved low-memory observations cleared.")
    }

    fun clearTemporaryFiles() { viewModelScope.launch {
        try {
            val bytes = withContext(Dispatchers.IO) { repository.clearOwnTemporaryFiles() }
            message("Cleared ${android.text.format.Formatter.formatShortFileSize(getApplication(), bytes)} of StayOpen temporary files.")
        } catch (_: Exception) { message("Couldn't clear every temporary file. No personal files were touched.") }
    } }

    fun exportReport(uri: Uri) { viewModelScope.launch {
        val s = state.value
        try {
            withContext(Dispatchers.IO) {
                val report = ReportExporter.create(BuildConfig.VERSION_NAME, device, s.snapshot, s.preferences,
                    s.apps, s.restrictions, s.samples, s.events, s.usage, s.exits)
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(report) }
                    ?: error("No writable destination")
            }
            message("Diagnostic report saved to your chosen location.")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message("Couldn't save the report. Try another location.") }
    } }

    private data class ContextData(val apps: List<AppEntry>, val restrictions: List<AppRestriction>, val permission: Boolean, val usage: List<UsageEntry>, val exits: List<OwnExit>)
}
