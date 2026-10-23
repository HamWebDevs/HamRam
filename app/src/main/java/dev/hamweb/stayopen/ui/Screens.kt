package dev.hamweb.stayopen.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hamweb.stayopen.BuildConfig
import dev.hamweb.stayopen.StayOpenState
import dev.hamweb.stayopen.StayOpenViewModel
import dev.hamweb.stayopen.data.DeviceInfo
import dev.hamweb.stayopen.data.PreferenceStore
import dev.hamweb.stayopen.data.SettingsNavigator
import dev.hamweb.stayopen.domain.*

@Composable
fun DashboardScreen(s: StayOpenState, model: StayOpenViewModel, onCsv: () -> Unit) {
    Page("SYSTEM MONITOR", "Real telemetry. Local control. Zero noise.") {
        item { MonitorStatus(s) }
        item {
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("MEMORY / LIVE")
                    Badge(if (s.monitoring) "Visible app only" else "Paused")
                }
                val snapshot = s.snapshot
                if (snapshot == null) {
                    Text(if (s.samplingError == null) "Reading Android's memory information…" else "No reading available yet.", style = MaterialTheme.typography.titleLarge)
                    if (s.samplingError == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(when (snapshot.memory.pressure) {
                                Pressure.NORMAL -> "Memory\nnominal"
                                Pressure.ELEVATED -> "Headroom\nreduced"
                                Pressure.HIGH -> "Pressure\nwarning"
                            }, style = MaterialTheme.typography.headlineMedium)
                            Text("Estimated pressure", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Badge(snapshot.memory.pressure.label, snapshot.memory.pressure == Pressure.HIGH)
                        }
                        MemoryRing(snapshot.memory, Modifier.size(150.dp))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("Available", gib(snapshot.memory.availableBytes), Modifier.weight(1f))
                        Stat("In use", gib(snapshot.memory.usedBytes), Modifier.weight(1f))
                        Stat("Total RAM", gib(snapshot.memory.totalBytes), Modifier.weight(1f))
                    }
                    Text("Measured ${time(snapshot.memory.timeMs)} · Android's reported physical RAM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (snapshot.memory.systemLowMemory) Note("Android is reporting low memory right now. Save your work before switching apps.", Icons.Default.Warning)
                }
                s.samplingError?.let { Note(it, Icons.Default.Warning) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = model::toggleMonitoring) { Text(if (s.monitoring) "Pause monitoring" else "Start monitoring") }
                    TextButton(onClick = model::refresh, enabled = !s.refreshing) { Text("Refresh") }
                }
                Note("RAM in use includes useful caches. Empty RAM isn't a performance goal.")
            }
        }
        item {
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Your RAM, over time", style = MaterialTheme.typography.titleLarge)
                    Text("${s.samples.size} / 360", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
                MemoryGraph(s.samples, AutoCarePolicy.sampleInterval(s.preferences.activeProfile.intervalSeconds, s.preferences.autoCareEnabled, s.snapshot?.memory?.pressure))
                Text("In-use RAM · ${s.preferences.activeProfile.intervalSeconds}s interval · gaps are unmonitored. The graph stays in memory for this app session.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { MemoryComparison(s, model, onCsv) }
        s.snapshot?.let { snapshot ->
            item { HardwareTelemetry(snapshot) }
            item {
                Panel {
                    Eyebrow("INTERNAL STORAGE")
                    Text("${gib(snapshot.storageAvailableBytes)} available", style = MaterialTheme.typography.titleLarge)
                    val used = if (snapshot.storageTotalBytes > 0) 1f - snapshot.storageAvailableBytes.toFloat() / snapshot.storageTotalBytes else 0f
                    LinearProgressIndicator(progress = { used.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(7.dp), color = MaterialTheme.colorScheme.secondary)
                    Text("${gib(snapshot.storageTotalBytes)} total on Android's internal data volume. System partitions and removable storage are excluded.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Panel {
                Eyebrow("YOUR DEVICE")
                Text(model.device.friendlyModel, style = MaterialTheme.typography.titleLarge)
                Text("${model.device.model} · Android ${model.device.androidVersion} · API ${model.device.sdk}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${s.preferences.activeProfile.name} profile", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProtectedAppsScreen(s: StayOpenState, model: StayOpenViewModel, navigator: SettingsNavigator) {
    var picker by rememberSaveable { mutableStateOf(false) }
    var edit by rememberSaveable { mutableStateOf(false) }
    var reviewPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val profile = s.preferences.activeProfile
    val selected = profile.packages.map { name -> s.apps.firstOrNull { it.packageName == name } ?: AppEntry(name, name) }.sortedBy { it.label.lowercase() }
    Page("APP GUARD", "Your essentials. Your profiles. Android stays in control.") {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow("CHOOSE A PROFILE")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.preferences.profiles.forEach { p -> FilterChip(selected = profile.id == p.id, onClick = { model.selectProfile(p.id) }, label = { Text(p.name) }) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Preferred apps · ${selected.size}", style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(onClick = { picker = true }) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Add apps") }
            }
        }
        item {
            Panel {
                Eyebrow("${profile.reviewedPackages.intersect(profile.packages).size} / ${profile.packages.size} SETUPS SELF-REVIEWED")
                Text(profile.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (profile.notes.isNotBlank()) Text(profile.notes, style = MaterialTheme.typography.bodyMedium)
                Note("Apps in every profile are excluded from StayOpen's RAM clearing. Profiles also save guidance; they cannot reserve RAM or stop Android closing an app.")
                OutlinedButton(onClick = { edit = true }) { Text("Edit profile & preferences") }
            }
        }
        if (selected.isEmpty()) item {
            Panel {
                Icon(Icons.Default.FavoriteBorder, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Start with your essentials", style = MaterialTheme.typography.titleLarge)
                Text("Choose messaging, music, navigation or a game. A short, deliberate list is easier on your battery.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(selected, key = { it.packageName }) { app ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(app.packageName, app.label)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(app.label, style = MaterialTheme.typography.titleMedium)
                        Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val restriction = s.restrictions.firstOrNull { it.packageName == app.packageName }
                Text(when (restriction?.batteryExempt) {
                    true -> "Verified: on Android's Doze exemption list"
                    false -> "Verified: not on Android's Doze exemption list"
                    null -> "Doze exemption status is unavailable"
                }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (app.packageName in profile.reviewedPackages) Badge("Setup reviewed by you")
                if (s.apps.none { it.packageName == app.packageName }) Note("This app is no longer in the visible launcher list. It may be uninstalled or hidden.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { reviewPackage = app.packageName }) { Text("Review setup") }
                    TextButton(onClick = { model.message(navigator.app(app.packageName)) }) { Text("App settings") }
                    TextButton(onClick = { model.message(navigator.launchApp(app.packageName)) }) { Text("Launch app") }
                }
            }
        }
        item { Note("Your list is a setup plan, not a running-app list. Android can still close any app when it needs memory.") }
    }
    if (picker) AppPicker(s.apps, profile.packages, model::toggleApp, { picker = false })
    if (edit) ProfileEditor(profile, { name, notes, seconds -> model.saveProfileDetails(name, notes); model.setInterval(seconds); edit = false }, { edit = false })
    reviewPackage?.let { packageName ->
        val app = selected.firstOrNull { it.packageName == packageName } ?: AppEntry(packageName, packageName)
        AppReviewDialog(app, model.device, s.preferences.oneUi, packageName in profile.reviewedPackages,
            onReviewed = { model.markReviewed(packageName, it) }, onSettings = { model.message(navigator.app(packageName)) },
            onBattery = { model.message(navigator.battery()) }, onSamsung = { model.message(navigator.settings()) },
            onRemove = { model.toggleApp(packageName); reviewPackage = null }, onDismiss = { reviewPackage = null })
    }
}

@Composable
private fun AppPicker(apps: List<AppEntry>, selected: Set<String>, onToggle: (String) -> Unit, dismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) { apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) } }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Choose your apps") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search apps") }, singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) })
                Text("${selected.size} selected in this profile. Shows launchable apps in your current Android user.", style = MaterialTheme.typography.bodyMedium)
                LazyColumn(Modifier.heightIn(min = 80.dp, max = 380.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (filtered.isEmpty()) item { Text("No matching launchable apps. Try Refresh after returning to StayOpen.", Modifier.padding(vertical = 20.dp)) }
                    items(filtered, key = { it.packageName }) { app ->
                        Row(Modifier.fillMaxWidth().clickable { onToggle(app.packageName) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppIcon(app.packageName, app.label)
                            Text(app.label, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Checkbox(checked = app.packageName in selected, onCheckedChange = { onToggle(app.packageName) })
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = dismiss) { Text("Done") } })
}

@Composable
private fun ProfileEditor(profile: Profile, save: (String, String, Int) -> Unit, dismiss: () -> Unit) {
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var notes by rememberSaveable(profile.id) { mutableStateOf(profile.notes) }
    var seconds by rememberSaveable(profile.id) { mutableIntStateOf(profile.intervalSeconds) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Make this profile yours") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Profile name") }, singleLine = true)
            OutlinedTextField(notes, { notes = it.take(2000) }, label = { Text("Your setup notes") }, minLines = 3, maxLines = 5)
            Text("Monitoring interval", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                PreferenceStore.INTERVALS.forEach { value -> FilterChip(seconds == value, onClick = { seconds = value }, label = { Text("${value}s") }) }
            }
            Text("Changes apply within the next monitoring interval. App choices are saved separately for each profile.", style = MaterialTheme.typography.bodyMedium)
        }
    }, confirmButton = { TextButton(onClick = { save(name, notes, seconds) }, enabled = name.isNotBlank()) { Text("Save profile") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
private fun AppReviewDialog(app: AppEntry, device: DeviceInfo, oneUi: OneUi, reviewed: Boolean,
    onReviewed: (Boolean) -> Unit, onSettings: () -> Unit, onBattery: () -> Unit, onSamsung: () -> Unit,
    onRemove: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Set up ${app.label}") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("1. Open App settings → Battery. If this app needs timely background work, review Unrestricted / Allow background usage where offered.")
            OutlinedButton(onClick = onSettings) { Text("Open this app's settings") }
            Text("2. Review Android's battery optimisation list. Exempt only the apps that need it; exemptions can increase battery use.")
            OutlinedButton(onClick = onBattery) { Text("Battery optimisation list") }
            if (device.isSamsung) {
                Text("3. ${device.sleepingPath(oneUi)}. Remove this app from Sleeping apps and Deep sleeping apps, then review Never sleeping / Never auto sleeping apps.")
                Text("Some apps already set to Unrestricted may be absent from Samsung's selectable list. Menu labels vary; use Settings search if needed.", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onSamsung) { Text("Open Samsung Settings") }
            }
            Note("Only Android's Doze exemption is independently checked here. The steps above are manual. A checked box below records your review, not verified protection.")
            Row(Modifier.fillMaxWidth().clickable { onReviewed(!reviewed) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(reviewed, onCheckedChange = onReviewed)
                Text("I reviewed this app's setup", Modifier.weight(1f))
            }
            TextButton(onClick = onRemove) { Text("Remove from this profile") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
fun OptimiseScreen(s: StayOpenState, model: StayOpenViewModel, navigator: SettingsNavigator, onApps: () -> Unit) {
    var guide by rememberSaveable { mutableStateOf<String?>(null) }
    Page("CARE CONSOLE", "Grant access. Clear cached apps. Keep your essentials.") {
        item { RamClearPanel(s, model, navigator) }
        item { RamAutomaticPanel(s, model, navigator) }
        item { AutoCarePanel(s, model) }
        if (model.device.isSamsung) item {
            Panel {
                Eyebrow("SAMSUNG ASSISTANT")
                Text(model.device.friendlyModel, style = MaterialTheme.typography.titleLarge)
                Text(model.device.modelAdvice, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Sleeping-app lists", style = MaterialTheme.typography.titleMedium)
                Text(model.device.sleepingPath(s.preferences.oneUi))
                Text("Remove essentials from Sleeping and Deep sleeping apps. Review Never sleeping / Never auto sleeping apps for apps that need background work. Avoid exempting everything.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Note(if (s.preferences.oneUi == OneUi.AUTO) "Menu layout is estimated from Android ${model.device.androidVersion}. Choose your installed One UI in Settings if these paths differ." else "Guidance uses your selected ${s.preferences.oneUi.label}. Exact menus can vary by firmware and region.")
                Button(onClick = { model.message(navigator.settings()) }) { Text("Open Samsung Settings") }
            }
        }
        item { SettingAction("Review preferred apps", "Walk through battery and background settings for each app.", Icons.Default.FavoriteBorder, onApps) }
        item { SettingAction("Manage internal storage", "Review space in Android's own storage tools.", Icons.Default.Settings) { model.message(navigator.storage()) } }
        item { SettingAction("Clear an app's cache", "Follow a safe, manual cache-clearing guide.", Icons.Default.Info) { guide = "cache" } }
        item { SettingAction("Battery optimisation", "Review Android's Doze exemption list.", Icons.Default.Settings) { model.message(navigator.battery()) } }
        item { SettingAction("Power saving", "Review battery saving if background work is delayed.", Icons.Default.Settings) { model.message(navigator.powerSaving()) } }
        item { SettingAction("Activity lifetime settings", "Check Don't keep activities and the background process limit.", Icons.Default.Settings) { guide = "developer" } }
        if (model.device.isSamsung) item { SettingAction("Understand RAM Plus", "Storage-backed virtual memory, with real trade-offs.", Icons.Default.Info) { guide = "ramplus" } }
        item { SettingAction("Clear StayOpen temporary files", "Only this app's cache. Your files and profiles stay safe.", Icons.Default.Delete) { model.clearTemporaryFiles() } }
        item { Panel { Text("When apps keep reloading", style = MaterialTheme.typography.titleLarge); Text("Save work, reduce heavy browser tabs and game textures, then compare readings. Check for updates to the affected app and your phone. A restart can help a temporary fault; repeatedly clearing RAM can make apps reload more often.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        item { EventConsole(s) }
    }
    if (guide != null) AlertDialog(onDismissRequest = { guide = null },
        title = { Text(when (guide) { "cache" -> "Clear cache carefully"; "developer" -> "Activity lifetime settings"; else -> "Samsung RAM Plus" }) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (guide == "cache") {
                Text("Open Android Settings → Apps → choose the affected app → Storage → Clear cache.")
                Text("Clear cache removes temporary data. Clear data / Clear storage can remove sign-ins, downloads or other app data. Read the button carefully.")
                Text("Clearing a large or broken cache can help storage issues, but doing it repeatedly may slow app loading. StayOpen cannot clear another app's cache itself.")
            } else if (guide == "developer") {
                Text("If Developer options are already enabled, check Don't keep activities is off and Background process limit is set to Standard limit.")
                Text("Don't keep activities can destroy an app's screen when you leave it. An artificially low process limit can also cause extra reloads. StayOpen can read the first setting where Android exposes it, but cannot read the full process-limit policy or change either setting.")
                Text("You do not need to enable Developer options to use StayOpen. If this screen is unavailable, return to Android Settings.")
            } else {
                Text(model.device.ramPlusPath(s.preferences.oneUi))
                Text("RAM Plus lets Samsung use storage to support virtual memory. It is slower than physical RAM and does not add real RAM or guarantee an app stays open.")
                Text("A larger setting is not always faster. Availability, values and an off switch depend on the phone and firmware. Review Samsung's own explanation and compare normal use after one change at a time. A restart may be requested.")
                Text("StayOpen cannot reliably read or change the RAM Plus setting. If the path is missing, search Settings for RAM Plus.")
            }
        } }, confirmButton = { TextButton(onClick = { model.message(when (guide) { "cache" -> navigator.apps(); "developer" -> navigator.developer(); else -> navigator.settings() }) }) { Text("Open Android Settings") } },
        dismissButton = { TextButton(onClick = { guide = null }) { Text("Close") } })
}

@Composable
fun DiagnosticsScreen(s: StayOpenState, model: StayOpenViewModel, navigator: SettingsNavigator, onApps: () -> Unit, onExport: () -> Unit, onUsage: () -> Unit) {
    val findings = remember(s.snapshot, s.restrictions) { s.snapshot?.let { DiagnosticsEngine.findings(it, s.restrictions, model.device.isSamsung) }.orEmpty() }
    Page("SYSTEM DIAGNOSTICS", "Measured signals. Ranked fixes. A real event trail.") {
        item {
            Panel {
                Eyebrow("PHONE HEALTH REPORT")
                Text(if (s.snapshot == null) "Waiting for a reading" else DiagnosticsEngine.headline(findings), style = MaterialTheme.typography.headlineMedium)
                Text("A summary of the latest snapshot, not a hardware test or a diagnosis. Your readings may change while you use other apps.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                s.snapshot?.let { snap ->
                    Text("Memory: ${snap.memory.pressure.label.lowercase()} estimate · Android low-memory flag: ${if (snap.memory.systemLowMemory) "on" else "off"}", style = MaterialTheme.typography.bodyMedium)
                    Text("Android threshold: ${gib(snap.memory.thresholdBytes)} · Power saving: ${if (snap.powerSaver) "on" else "off"}", style = MaterialTheme.typography.bodyMedium)
                    Text("Thermal status: ${thermalLabel(snap.thermalStatus)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Don't keep activities: ${when (snap.alwaysFinishActivities) { true -> "ON · review this"; false -> "off"; null -> "unavailable" }}", style = MaterialTheme.typography.bodyMedium)
                    Text("Last measured ${dateTime(snap.memory.timeMs)}${if (!s.monitoring) " · monitoring paused" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onExport) { Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Export diagnostic report") }
            }
        }
        item { Text("Recommended next steps", style = MaterialTheme.typography.titleLarge) }
        items(findings, key = { it.id }) { finding ->
            Panel {
                Badge(finding.evidence.label, finding.priority >= 90)
                Text(finding.title, style = MaterialTheme.typography.titleLarge)
                Text(finding.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (finding.action != Action.NONE) TextButton(onClick = {
                    when (finding.action) {
                        Action.APPS -> onApps()
                        Action.STORAGE -> model.message(navigator.storage())
                        Action.BATTERY -> model.message(navigator.powerSaving())
                        Action.SAMSUNG -> model.message(navigator.settings())
                        Action.DEVELOPER -> model.message(navigator.developer())
                        Action.NONE -> Unit
                    }
                }) { Text(when (finding.action) { Action.APPS -> "Review apps"; Action.STORAGE -> "Manage storage"; Action.SAMSUNG -> "Open Samsung Settings"; Action.DEVELOPER -> "Review Developer options"; else -> "Review battery settings" }) }
            }
        }
        item { EventConsole(s) }
        item {
            Panel {
                Eyebrow("LOW-MEMORY HISTORY")
                Text("${s.events.size} observed warning(s)", style = MaterialTheme.typography.titleLarge)
                Text("Saved when Android's lowMemory flag turns on while visible monitoring is active. Returning during an ongoing warning creates a new observation. Up to 100 are kept locally.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (s.events.isEmpty()) Text("No warnings recorded. Time outside StayOpen is not monitored.", style = MaterialTheme.typography.bodyMedium)
                s.events.takeLast(6).reversed().forEach { event ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("${dateTime(event.timeMs)} · ${gib(event.availableBytes)} available", style = MaterialTheme.typography.bodyMedium)
                }
                if (s.events.size > 6) Text("Export the report to see all ${s.events.size} observations.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Panel {
                Eyebrow("OPTIONAL APP USAGE")
                Text("Foreground time", style = MaterialTheme.typography.titleLarge)
                Text("Android's aggregated usage queried for the last 24 hours. Its daily buckets may include time outside this window. This is not app RAM use or evidence of a closure.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                when {
                    !s.preferences.usageEnabled -> { Text("Off by default. Usage access is optional."); OutlinedButton(onClick = onUsage) { Text("Enable usage insights") } }
                    !s.usagePermission -> { Note("Usage access has not been granted. All other diagnostics work without it."); OutlinedButton(onClick = onUsage) { Text("Review usage access") } }
                    s.usage.isEmpty() -> Text("No launchable-app usage was returned by Android. Try Refresh after using your phone.")
                    else -> s.usage.take(8).forEach { row ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppIcon(row.packageName, row.label)
                            Text(row.label, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${row.foregroundMs / 60_000} min", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        item {
            Panel {
                Eyebrow("STAYOPEN'S OWN EXIT RECORDS")
                if (model.device.sdk < 30) Text("Android 11 or newer is needed for this app's historical exit reasons.")
                else if (s.exits.isEmpty()) Text("Android has no recent exit records for StayOpen.")
                else s.exits.forEach { Text("${dateTime(it.timeMs)} · ${it.reason}", style = MaterialTheme.typography.bodyMedium) }
                Note("Android does not let this app inspect other apps' exit records. A sudden closure cannot be attributed to memory from a RAM graph alone.")
            }
        }
    }
}

fun thermalLabel(status: Int?): String = when (status) { null -> "Unavailable"; 0 -> "None"; 1 -> "Light"; 2 -> "Moderate"; 3 -> "Severe"; 4 -> "Critical"; 5 -> "Emergency"; 6 -> "Shutdown"; else -> "Unknown ($status)" }

@Composable
fun SettingsScreen(s: StayOpenState, model: StayOpenViewModel, navigator: SettingsNavigator, onUsage: () -> Unit, onBackupExport: () -> Unit, onBackupImport: () -> Unit) {
    var clear by rememberSaveable { mutableStateOf(false) }
    Page("CONTROL PANEL", "Tune the console. Keep your setup portable.") {
        item {
            Panel {
                Text("Appearance", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode -> FilterChip(s.preferences.theme == mode, onClick = { model.setTheme(mode) }, label = { Text(mode.name.lowercase().replaceFirstChar { it.titlecase() }) }) }
                }
                Text("Terminal accent", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Accent.entries.forEach { accent -> FilterChip(s.preferences.accent == accent, onClick = { model.setAccent(accent) }, label = { Text(accent.label) }) }
                }
            }
        }
        item {
            Panel {
                Eyebrow("SETUP / BACKUP & RESTORE")
                Text("Keep your profiles", style = MaterialTheme.typography.titleLarge)
                Text("Export app lists, profile notes, appearance and care preferences to a small JSON file. Importing asks before replacing the current setup. Android permissions and system settings are never changed by a backup.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBackupExport) { Text("Export setup") }
                    OutlinedButton(onClick = onBackupImport) { Text("Import setup") }
                }
            }
        }
        item {
            Panel {
                Text("Visible monitoring", style = MaterialTheme.typography.titleLarge)
                Text("Dashboard samples run only while StayOpen is on screen. Optional Auto clear in Optimise uses a separate, user-started service with a Stop notification. No wake locks. Choose a gentler dashboard interval for the ${s.preferences.activeProfile.name} profile.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreferenceStore.INTERVALS.forEach { seconds -> FilterChip(s.preferences.activeProfile.intervalSeconds == seconds, onClick = { model.setInterval(seconds) }, label = { Text("${seconds}s") }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Automatic housekeeping", Modifier.weight(1f))
                    Switch(s.preferences.autoCareEnabled, onCheckedChange = model::setAutoCare)
                }
                Text("Auto care operates only while visible. High memory pressure backs sampling off to at least 10 seconds. See Optimise for exact actions and run history.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                s.snapshot?.let { Text("StayOpen managed heap: ${android.text.format.Formatter.formatShortFileSize(androidx.compose.ui.platform.LocalContext.current, it.appHeapBytes)}. This excludes native, graphics and shared memory.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (model.device.isSamsung) item {
            Panel {
                Text("Samsung guidance", style = MaterialTheme.typography.titleLarge)
                Text("Exact One UI version cannot be read with a standard public API. Check Settings → About phone → Software information and select it here if needed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OneUi.entries.forEach { choice ->
                    Row(Modifier.fillMaxWidth().clickable { model.setOneUi(choice) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(s.preferences.oneUi == choice, onClick = { model.setOneUi(choice) })
                        Text(choice.label, Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Panel {
                Text("Privacy & usage access", style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Optional usage insights", Modifier.weight(1f))
                    Switch(s.preferences.usageEnabled, onCheckedChange = { if (it) onUsage() else model.setUsage(false) })
                }
                Text("System usage permission: ${if (s.usagePermission) "granted" else "not granted"}. Turning insights off stops reads and clears the displayed usage list. To revoke Android's permission too, open usage access settings.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { model.message(navigator.usageAccess()) }) { Text("Android usage access settings") }
                Note("No internet permission, accounts, ads, analytics or subscriptions. Preferences and up to 100 warning observations stay on your device. Android backup is disabled. Reports leave only through the location you choose.")
            }
        }
        item { SettingAction("Clear diagnostic history", "Remove RAM samples, warning observations and care / clearing history.", Icons.Default.Delete) { clear = true } }
        item {
            Panel {
                Eyebrow("STAYOPEN ${BuildConfig.VERSION_NAME}")
                Text("Better information. Fewer surprises.", style = MaterialTheme.typography.titleLarge)
                Text("A native, offline Android utility by HamWebDevs. Your preferred-app list guides setup; it cannot override Android's low-memory killer or guarantee apps remain alive.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Sources: Android public API documentation and Samsung's sleeping-app guidance. Menu names can change between software versions.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Clear diagnostic history?") },
        text = { Text("This removes low-memory observations, care history, the session baseline and the RAM graph. Profiles and app choices are kept. Active monitoring will collect new readings.") },
        confirmButton = { TextButton(onClick = { model.clearHistory(); clear = false }) { Text("Clear history") } },
        dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancel") } })
}
