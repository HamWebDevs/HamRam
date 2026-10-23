package dev.hamweb.stayopen

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import dev.hamweb.stayopen.data.PreferenceStore
import dev.hamweb.stayopen.data.SystemRepository
import dev.hamweb.stayopen.data.PreferencesCodec
import dev.hamweb.stayopen.domain.*

@RunWith(AndroidJUnit4::class)
class StayOpenSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun opensAllFiveScreensAndProfileEditorWithoutUsagePermission() {
        compose.waitUntil(20_000) { compose.onAllNodesWithText("SYSTEM MONITOR").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Navigate to Protected Apps").performClick()
        compose.onNodeWithText("Edit profile & preferences").performClick()
        compose.onNodeWithText("Make this profile yours").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        capture("protected-apps")
        compose.onNodeWithText("Add apps").performClick()
        compose.onNodeWithText("Choose your apps").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Navigate to Optimise").performClick()
        compose.onNodeWithText("CARE CONSOLE").assertIsDisplayed()
        compose.onNodeWithText("Run safe care").assertExists()
        capture("optimise")
        compose.onNodeWithContentDescription("Navigate to Diagnostics").performClick()
        compose.onNodeWithText("Export diagnostic report").assertExists()
        capture("diagnostics")
        compose.onNodeWithContentDescription("Navigate to Settings").performClick()
        compose.onNodeWithText("CONTROL PANEL").assertIsDisplayed()
        capture("settings")
        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithContentDescription("Navigate to Dashboard").performClick()
        capture("dashboard-dark")
        compose.onNodeWithContentDescription("Navigate to Settings").performClick()
        compose.onNodeWithText("Light").performClick()
        compose.onNodeWithContentDescription("Navigate to Dashboard").performClick()
        capture("dashboard-light")
        compose.onNodeWithContentDescription("Navigate to Settings").performClick()
        compose.onNodeWithText("System").performClick()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        // Shell-owned test output survives AGP uninstalling the instrumented app after the tests.
        for (command in listOf("mkdir -p /sdcard/Download/StayOpen-test-screenshots",
            "screencap -p /sdcard/Download/StayOpen-test-screenshots/$name.png")) {
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
    }

    @Test fun realSystemSnapshotContainsPlausiblePhysicalMeasurements() {
        val snapshot = SystemRepository(compose.activity).snapshot()
        assertTrue(snapshot.memory.totalBytes > 0)
        assertTrue(snapshot.memory.availableBytes in 0..snapshot.memory.totalBytes)
        assertTrue(snapshot.storageTotalBytes > 0)
        assertTrue(snapshot.storageAvailableBytes in 0..snapshot.storageTotalBytes)
        snapshot.batteryPercent?.let { assertTrue(it in 0..100) }
        assertTrue(snapshot.uptimeMs > 0)
    }

    @Test fun preferencesAndLowMemoryObservationsSurviveStoreRecreation() {
        val store = PreferenceStore(compose.activity)
        val original = store.load()
        val events = store.loadEvents()
        try {
            val saved = Preferences(theme = ThemeMode.DARK, profiles = defaultProfiles().map {
                if (it.id == "custom") it.copy(packages = setOf("example.app"), notes = "Local note", intervalSeconds = 30) else it
            }, activeProfileId = "custom")
            store.save(saved)
            assertEquals(saved, PreferenceStore(compose.activity).load())
            val observed = listOf(LowMemoryEvent(1234, 1024, 2048))
            store.saveEvents(observed)
            assertEquals(observed, PreferenceStore(compose.activity).loadEvents())
        } finally { store.save(original); store.saveEvents(events) }
    }

    @Test fun memorySamplingStopsWhenActivityIsNotVisible() {
        lateinit var model: StayOpenViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[StayOpenViewModel::class.java] }
        compose.waitUntil(20_000) { model.state.value.samples.isNotEmpty() }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        val count = model.state.value.samples.size
        val careCount = model.state.value.careRuns.size
        android.os.SystemClock.sleep(6_000)
        assertEquals(count, model.state.value.samples.size)
        assertEquals(careCount, model.state.value.careRuns.size)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(15_000) { model.state.value.samples.size > count }
    }

    @Test fun temporaryFileCleanupPreservesPreferencesAndPrivateFiles() {
        val context = compose.activity
        val store = PreferenceStore(context)
        val saved = store.load()
        val keep = File(context.filesDir, "stayopen-cleanup-test.txt")
        val temporary = File(context.cacheDir, "stayopen-temp-test.txt")
        try {
            keep.writeText("keep my data")
            temporary.writeText("temporary bytes")
            assertTrue(SystemRepository(context).clearOwnTemporaryFiles() >= 15)
            assertFalse(temporary.exists())
            assertEquals("keep my data", keep.readText())
            assertEquals(saved, store.load())
        } finally { keep.delete(); temporary.delete() }
    }

    @Test fun setupBackupRoundTripsAllProfilesAndRejectsUntrustedShapes() {
        val setup = Preferences(accent = Accent.AMBER, autoCareEnabled = false,
            profiles = defaultProfiles().map { if (it.id == "gaming") it.copy(packages = setOf("example.game"), reviewedPackages = setOf("example.game"), notes = "My settings") else it })
        val encoded = PreferencesCodec.encode(setup)
        assertEquals(setup, PreferencesCodec.decode(encoded, requireBackup = true))
        for (invalid in listOf("{}", "[1,2,3]", "[".repeat(30) + "]".repeat(30), "x".repeat(200_001))) {
            assertTrue(runCatching { PreferencesCodec.decode(invalid, requireBackup = true) }.isFailure)
        }
        val legacy = "{\"theme\":\"SYSTEM\",\"active\":\"missing\"}"
        val migrated = PreferencesCodec.decode(legacy)
        assertEquals(ThemeMode.DARK, migrated.theme)
        assertEquals("everyday", migrated.activeProfileId)
    }

    @Test fun manualCareRecordsRealCacheDeletionAndABaseline() {
        lateinit var model: StayOpenViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[StayOpenViewModel::class.java] }
        compose.waitUntil(20_000) { model.state.value.snapshot != null }
        val originalAuto = model.state.value.preferences.autoCareEnabled
        compose.runOnIdle { model.setAutoCare(false) }
        compose.waitUntil(10_000) { !model.state.value.careRunning }
        val temporary = File(compose.activity.cacheDir, "care-test.bin")
        try {
            temporary.writeBytes(ByteArray(8192) { 42 })
            val start = System.currentTimeMillis()
            compose.runOnIdle { model.captureBaseline(); model.runCare() }
            compose.waitUntil(15_000) { model.state.value.careRuns.any { it.timeMs >= start && it.reason == CareReason.MANUAL } }
            val run = model.state.value.careRuns.last()
            assertTrue(run.clearedBytes >= 8192)
            assertTrue(run.beforeAvailableBytes > 0)
            assertFalse(temporary.exists())
            assertNotNull(model.state.value.baseline)
        } finally { temporary.delete(); compose.runOnIdle { model.setAutoCare(originalAuto) } }
    }
}
