package dev.hamweb.stayopen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.hamweb.stayopen.data.SettingsNavigator
import dev.hamweb.stayopen.ui.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val model: StayOpenViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { model.monitorWhileVisible() } }
        val navigator = SettingsNavigator(this)
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            StayOpenTheme(state.preferences.theme) { dark ->
                val view = LocalView.current
                SideEffect {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                StayOpenApp(state, model, navigator)
            }
        }
    }
}

private enum class Destination(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Home), APPS("Protected Apps", Icons.Default.FavoriteBorder),
    OPTIMISE("Optimise", Icons.Default.Build), DIAGNOSTICS("Diagnostics", Icons.Default.Info),
    SETTINGS("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StayOpenApp(state: StayOpenState, model: StayOpenViewModel, navigator: SettingsNavigator) {
    var destination by rememberSaveable { mutableStateOf(Destination.DASHBOARD) }
    var usageConsent by rememberSaveable { mutableStateOf(false) }
    var exportConsent by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) model.exportReport(uri)
    }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); model.message(null) } }
    BackHandler(destination != Destination.DASHBOARD) { destination = Destination.DASHBOARD }
    BoxWithConstraints {
        val wide = maxWidth >= 700.dp
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Icon(painterResource(R.drawable.ic_stayopen_mono), null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("StayOpen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    } },
                    actions = { IconButton(onClick = model::refresh, enabled = state.ready && !state.refreshing) {
                        Icon(Icons.Default.Refresh, "Refresh system readings")
                    } }, colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background))
            },
            bottomBar = {
                if (!wide) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
                    Destination.entries.forEach { item -> NavigationBarItem(
                        selected = destination == item, onClick = { destination = item },
                        icon = { Icon(item.icon, null) }, label = { Text(item.title, maxLines = 2, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.semantics { contentDescription = "Navigate to ${item.title}" }) }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Row(Modifier.padding(padding).fillMaxSize()) {
                if (wide) NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Destination.entries.forEach { item -> NavigationRailItem(
                        selected = destination == item, onClick = { destination = item }, icon = { Icon(item.icon, null) },
                        label = { Text(item.title, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.semantics { contentDescription = "Navigate to ${item.title}" }) }
                }
                Box(Modifier.weight(1f)) {
                    if (!state.ready) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    else when (destination) {
                        Destination.DASHBOARD -> DashboardScreen(state, model)
                        Destination.APPS -> ProtectedAppsScreen(state, model, navigator)
                        Destination.OPTIMISE -> OptimiseScreen(state, model, navigator) { destination = Destination.APPS }
                        Destination.DIAGNOSTICS -> DiagnosticsScreen(state, model, navigator,
                            onApps = { destination = Destination.APPS }, onExport = { exportConsent = true }, onUsage = { usageConsent = true })
                        Destination.SETTINGS -> SettingsScreen(state, model, navigator) { usageConsent = true }
                    }
                }
            }
        }
    }
    if (usageConsent) AlertDialog(onDismissRequest = { usageConsent = false }, title = { Text("Enable optional usage insights?") },
        text = { Text("Android usage access can reveal which apps you use and when. StayOpen reads foreground-time summaries only when you open it or refresh, keeps them in memory and includes displayed usage in a report only when you choose to export it. Nothing is uploaded. You can turn insights off here and revoke access in Android Settings. Memory and protection guidance work without this permission.") },
        confirmButton = { TextButton(onClick = {
            model.setUsage(true)
            if (!state.usagePermission) model.message(navigator.usageAccess())
            usageConsent = false
        }) { Text(if (state.usagePermission) "Enable insights" else "Continue to Android Settings") } },
        dismissButton = { TextButton(onClick = { usageConsent = false }) { Text("Not now") } })
    if (exportConsent) AlertDialog(onDismissRequest = { exportConsent = false }, title = { Text("Save a diagnostic report?") },
        text = { Text("The plain-text report includes your device model, memory readings, saved low-memory observations, current profile notes, selected app names, Doze checks and any enabled usage insights. Choose where to save it in Android's file picker. Cloud destinations are provided by other apps; choose local storage to keep it offline.") },
        confirmButton = { TextButton(onClick = {
            exportConsent = false
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            export.launch("StayOpen-report-$stamp.txt")
        }) { Text("Choose save location") } },
        dismissButton = { TextButton(onClick = { exportConsent = false }) { Text("Cancel") } })
}
