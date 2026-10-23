package dev.hamweb.stayopen.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.hamweb.stayopen.StayOpenState
import dev.hamweb.stayopen.StayOpenViewModel
import dev.hamweb.stayopen.domain.*
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonitorStatus(s: StayOpenState) {
    val interval = AutoCarePolicy.sampleInterval(s.preferences.activeProfile.intervalSeconds, s.preferences.autoCareEnabled, s.snapshot?.memory?.pressure)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Badge(if (s.monitoring) "MONITOR / ${interval}s" else "MONITOR / PAUSED")
        Badge(if (s.preferences.autoCareEnabled) "AUTO CARE / ON" else "AUTO CARE / OFF")
        Badge("OFFLINE / NO ROOT")
    }
}

@Composable
fun HardwareTelemetry(snapshot: Snapshot) {
    Panel {
        Eyebrow("DEVICE TELEMETRY")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Battery", snapshot.batteryPercent?.let { "$it%" } ?: "Unknown", Modifier.weight(1f))
            Stat("Battery temp", snapshot.batteryTemperatureC?.let { String.format(Locale.getDefault(), "%.1f °C", it) } ?: "Unknown", Modifier.weight(1f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Since last boot", uptime(snapshot.uptimeMs), Modifier.weight(1f))
            Stat("Thermal status", thermalLabel(snapshot.thermalStatus), Modifier.weight(1f))
        }
        Text(when (snapshot.charging) { true -> "POWER_SOURCE / CHARGING OR FULL"; false -> "POWER_SOURCE / BATTERY"; null -> "POWER_SOURCE / UNKNOWN" },
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text("Battery temperature is a battery sensor reading, not CPU temperature.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryComparison(s: StayOpenState, model: StayOpenViewModel, onCsv: () -> Unit) {
    Panel {
        Eyebrow("COMPARE / SESSION BASELINE")
        if (s.baseline == null) Text("Save a real reading, use your phone normally, then return to compare available RAM and storage.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            val baseline = s.baseline
            val current = s.snapshot
            Text("Baseline ${dateTime(baseline.memory.timeMs)}", style = MaterialTheme.typography.labelMedium)
            current?.let {
                Text("RAM available  ${gib(baseline.memory.availableBytes)} → ${gib(it.memory.availableBytes)}", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                Badge("RAM change  ${delta(it.memory.availableBytes - baseline.memory.availableBytes)}")
                Text("Storage available  ${gib(baseline.storageAvailableBytes)} → ${gib(it.storageAvailableBytes)}", style = MaterialTheme.typography.bodyMedium)
            }
            Note("These are observed changes across the phone, not proof that StayOpen freed RAM. A baseline lasts for this app session.")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = model::captureBaseline, enabled = s.snapshot != null) { Text(if (s.baseline == null) "Save baseline" else "Replace baseline") }
            TextButton(onClick = onCsv, enabled = s.samples.isNotEmpty()) { Text("Export RAM CSV") }
        }
    }
}

@Composable
fun AutoCarePanel(s: StayOpenState, model: StayOpenViewModel) {
    val context = LocalContext.current
    Panel {
        Eyebrow("AUTO CARE / FOREGROUND ONLY")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Automatic housekeeping", style = MaterialTheme.typography.titleLarge)
                Text(if (s.preferences.autoCareEnabled) "Enabled while StayOpen is visible" else "Manual mode", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(s.preferences.autoCareEnabled, onCheckedChange = model::setAutoCare, modifier = Modifier.semantics { contentDescription = "Automatic housekeeping" })
        }
        Text("Deletes StayOpen's own temporary files and drops reusable icon-cache references. Runs on first use, every 15 minutes while visible, or on a new high-pressure reading after a 2-minute cooldown.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text("During high pressure, automatic care also slows StayOpen sampling to at least 10 seconds. It does not kill other apps, change system settings or guarantee free RAM.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = model::runCare, enabled = !s.careRunning) { Text(if (s.careRunning) "Running safe care…" else "Run safe care") }
        s.careRuns.lastOrNull()?.let { run ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("LAST RUN / ${dateTime(run.timeMs)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(run.reason.label, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("Own disk cache deleted", android.text.format.Formatter.formatShortFileSize(context, run.clearedBytes), Modifier.weight(1f))
                Stat("Reusable icons dropped", run.iconsDropped.toString(), Modifier.weight(1f))
            }
            Text("Available RAM: ${gib(run.beforeAvailableBytes)} → ${run.afterAvailableBytes?.let(::gib) ?: "unavailable"}. System activity affects this change; it is not a care performance score.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (run.clearedBytes == 0L && run.iconsDropped == 0) Badge("Already lean · nothing to clear")
        }
    }
}

@Composable
fun EventConsole(s: StayOpenState) {
    val context = LocalContext.current
    val events = (s.events.map { it.timeMs to "WARN  Android low-memory flag / ${gib(it.availableBytes)} available" } +
        s.careRuns.map { it.timeMs to "CARE  ${it.reason.label} / cache ${android.text.format.Formatter.formatShortFileSize(context, it.clearedBytes)} / icons ${it.iconsDropped}" })
        .sortedByDescending { it.first }.take(12)
    Panel {
        Eyebrow("EVENT CONSOLE")
        if (events.isEmpty()) Text("No saved events. Visible monitoring records real warnings and care runs here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else events.forEach { (at, event) ->
            Text("${dateTime(at)}\n$event", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium,
                color = if (event.startsWith("WARN")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        Text("Latest 12 events. Background time is unmonitored. Full saved history is included in the diagnostic report.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
