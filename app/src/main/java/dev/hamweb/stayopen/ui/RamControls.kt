package dev.hamweb.stayopen.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import dev.hamweb.stayopen.StayOpenState
import dev.hamweb.stayopen.StayOpenViewModel
import dev.hamweb.stayopen.data.SettingsNavigator
import dev.hamweb.stayopen.domain.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RamClearPanel(s: StayOpenState, model: StayOpenViewModel, navigator: SettingsNavigator) {
    var setup by rememberSaveable { mutableStateOf(false) }
    var apps by rememberSaveable { mutableStateOf(false) }
    val ram = s.ram
    val labels = s.apps.associate { it.packageName to it.label }
    Panel {
        Eyebrow("RAM CLEAR / SHIZUKU")
        Text("Make room. Keep your essentials.", style = MaterialTheme.typography.titleLarge)
        Badge(when (ram.access) {
            BridgeAccess.READY -> "PRIVILEGED ACCESS / GRANTED"
            BridgeAccess.PERMISSION, BridgeAccess.DENIED -> "ACCESS / AWAITING PERMISSION"
            else -> "ACCESS / ${ram.access.name.replace('_', ' ')}"
        })
        Text(ram.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ram.access == BridgeAccess.PERMISSION) Button(onClick = model.ramController::requestPermission) { Text("Grant Shizuku access") }
            if (ram.access != BridgeAccess.READY) OutlinedButton(onClick = { model.message(navigator.shizuku()) }) { Text("Open Shizuku") }
            TextButton(onClick = { setup = true }) { Text("Permission setup") }
            TextButton(onClick = model.ramController::refreshAccess) { Text("Recheck access") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("${s.preferences.ramClearApps.size} apps selected for clearing", style = MaterialTheme.typography.titleMedium)
        Text("Only selected, downloaded apps are eligible. Apps in any Protected Apps profile, system apps, your keyboard, launcher and default dialer are excluded. Active apps and foreground services are skipped.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { apps = true }, enabled = !ram.busy) { Text("Choose apps to clear") }
            TextButton(onClick = model.ramController::scan, enabled = ram.access == BridgeAccess.READY && !ram.busy && s.preferences.ramClearApps.isNotEmpty()) { Text("Scan cached apps") }
        }
        Button(onClick = model.ramController::clearNow, enabled = ram.access == BridgeAccess.READY && !ram.busy && s.preferences.ramClearApps.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(if (ram.busy) "Working…" else "Clear cached RAM now")
        }
        ram.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = model.ramController::dismissError) { Text("Dismiss") }
        }
        ram.candidates?.let { candidates ->
            Text("SCAN / ${ram.scannedAtMs?.let(::dateTime).orEmpty()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (candidates.isEmpty()) Text("No selected apps are cached and eligible right now. Nothing needs clearing.", style = MaterialTheme.typography.bodyMedium)
            else {
                Text("${candidates.size} eligible apps · ${candidates.sumOf { it.pids.size }} cached processes", style = MaterialTheme.typography.titleMedium)
                candidates.take(6).forEach { Text("${labels[it.packageName] ?: it.packageName} · ${it.pids.size} processes", style = MaterialTheme.typography.bodyMedium) }
                Text("Preview only. Eligibility is checked again before each clear; at most ${RamClearPolicy.MAX_APPS_PER_RUN} apps per run.", style = MaterialTheme.typography.bodySmall)
            }
        }
        ram.runs.lastOrNull()?.let { RamRunResult(it, labels) }
        Note("This really requests background-process termination. Cleared apps can reload and lose unsaved in-memory work; protect anything you need. Android may restart a process. There is no force-stop, personal-file deletion or guaranteed speed boost.")
    }
    if (apps) RamAppPicker(s, model) { apps = false }
    if (setup) AlertDialog(onDismissRequest = { setup = false }, title = { Text("Enable real RAM clearing") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("1. Install Shizuku from its official download page. StayOpen works without root; Shizuku supplies ADB-level privilege after you approve it.")
            TextButton(onClick = { model.message(navigator.web("https://shizuku.rikka.app/download/")) }) { Text("Official Shizuku download") }
            Text("2. On Android 11 or newer, connect to Wi-Fi and enable Developer options → Wireless debugging. On Samsung, enable Developer options by tapping Settings → About phone → Software information → Build number seven times.")
            Text("3. In Shizuku, choose Start via wireless debugging → Pairing. Follow its pairing-code steps, then tap Start. Firmware can restrict debugging; use Shizuku's official computer / USB method if wireless debugging is unavailable.")
            Text("4. Return here, tap Recheck access, then Grant Shizuku access. Approve StayOpen in Shizuku's permission dialog. If denied permanently, use Shizuku → Authorised applications.")
            Text("5. Add essentials to Protected Apps, choose other apps to clear, then try Clear cached RAM now. The result counts observed process exits, not a made-up amount of RAM freed.")
            Text("6. For automatic operation, start Auto clear below and allow its notification. Start Shizuku and Auto clear again after a reboot. Samsung may also require allowing Shizuku and StayOpen background battery use.")
            Text("Normal Android permissions cannot clear other apps' RAM on Android 14 and newer. Personal sideloading does not remove that restriction. No accessibility control or root permission is requested.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { model.message(navigator.web("https://shizuku.rikka.app/guide/setup/")) }) { Text("Official setup and troubleshooting") }
        } }, confirmButton = { TextButton(onClick = { model.message(navigator.shizuku()) }) { Text("Open Shizuku") } },
        dismissButton = { TextButton(onClick = { setup = false }) { Text("Done") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RamAutomaticPanel(s: StayOpenState, model: StayOpenViewModel, navigator: SettingsNavigator) {
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.startRamAutomation() else model.message("Allow notifications to show the RAM monitor's status and Stop control, then try again.")
    }
    Panel {
        Eyebrow("AUTO CLEAR / PRESSURE WATCH")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Auto clear RAM", style = MaterialTheme.typography.titleLarge)
                Text(if (s.ram.automatic) { if (s.ram.screenPaused) "Running · paused with screen off" else "Running with a Stop notification" } else "Off · start when you need it",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(s.ram.automatic, onCheckedChange = { enabled ->
                if (!enabled) model.ramController.stopAutomatic()
                else if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) model.message("Enable StayOpen notifications in Android Settings to make the Stop control visible.")
                else model.startRamAutomation()
            }, enabled = s.ram.automatic || (s.ram.access == BridgeAccess.READY && s.preferences.ramClearApps.isNotEmpty()),
                modifier = Modifier.semantics { contentDescription = "Auto clear RAM" })
        }
        Text("Clear when available RAM stays below", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RamClearPolicy.THRESHOLDS.forEach { value -> FilterChip(s.preferences.ramThresholdPercent == value, onClick = { model.setRamThreshold(value) }, label = { Text("$value%") }) }
        }
        Text("Check interval", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RamClearPolicy.CHECK_INTERVALS.forEach { value -> FilterChip(s.preferences.ramCheckSeconds == value, onClick = { model.setRamCheckInterval(value) }, label = { Text("${value}s") }) }
        }
        Text("Runs after two consecutive pressure readings: your threshold or Android's low-memory warning. Automatic runs have a 5-minute cooldown; manual clearing resets it. Uses the higher of your threshold and Android's own threshold. Skips checks while the screen is off and never wakes the device. No boot auto-start.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Note("Automatic clearing continues after you leave StayOpen while its service is running. Samsung or Android can still stop the service. Repeated app clearing can increase reloads and battery use; leave it off when it is not useful.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.ram.automatic) Button(onClick = model.ramController::stopAutomatic) { Text("Stop Auto clear") }
            TextButton(onClick = { model.message(navigator.notifications()) }) { Text("Notification settings") }
        }
    }
}

@Composable
fun RamRunResult(run: RamRun, labels: Map<String, String>) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Eyebrow("${if (run.automatic) "AUTO" else "MANUAL"} RAM CLEAR / ${dateTime(run.timeMs)}")
    Text("${run.exited} / ${run.requested} cached processes observed exited", style = MaterialTheme.typography.titleMedium)
    if (run.outcomes.isEmpty()) Text("No selected apps were eligible; no termination was requested.", style = MaterialTheme.typography.bodyMedium)
    run.outcomes.take(5).forEach { outcome -> Text("${labels[outcome.packageName] ?: outcome.packageName}: ${outcome.error ?: "${outcome.exited}/${outcome.requested} processes exited"}", style = MaterialTheme.typography.bodyMedium) }
    Text("Available RAM ${gib(run.beforeBytes)} → ${gib(run.afterBytes)}. The observed system change includes other activity; it is not a measurement of RAM freed by StayOpen.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RamAppPicker(s: StayOpenState, model: StayOpenViewModel, dismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val protected = remember(s.preferences.profiles) { s.preferences.profiles.flatMap { it.packages }.toSet() }
    val apps = remember(s.apps, query, s.preferences.ramClearApps) {
        val available = s.apps.filter { !it.systemApp && it.packageName != "moe.shizuku.privileged.api" }
        (available + s.preferences.ramClearApps.filter { name -> available.none { it.packageName == name } }.map { AppEntry(it, it) })
            .filter { it.label.contains(query, true) || it.packageName.contains(query, true) }.sortedBy { it.label.lowercase() }
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Apps eligible for RAM clearing") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${s.preferences.ramClearApps.size} selected. Selection permits clearing only when cached. Protected apps are always excluded.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(query, { query = it }, label = { Text("Find downloaded apps") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = model::chooseAllRamClearApps) { Text("Select all unprotected user apps") }
            LazyColumn(Modifier.heightIn(min = 70.dp, max = 310.dp)) {
                if (apps.isEmpty()) item { Text("No downloaded launchable apps found. Refresh after installing an app.") }
                items(apps, key = { it.packageName }) { app ->
                    val guarded = app.packageName in protected
                    Row(Modifier.fillMaxWidth().clickable(enabled = !guarded) { model.toggleRamClearApp(app.packageName) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppIcon(app.packageName, app.label)
                        Column(Modifier.weight(1f)) {
                            Text(app.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (guarded) Text("Excluded by a Protected Apps profile", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Checkbox(checked = app.packageName in s.preferences.ramClearApps && !guarded, onCheckedChange = { model.toggleRamClearApp(app.packageName) }, enabled = !guarded)
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Done") } })
}
