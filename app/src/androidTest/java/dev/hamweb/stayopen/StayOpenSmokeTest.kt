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
import dev.hamweb.stayopen.domain.*

@RunWith(AndroidJUnit4::class)
class StayOpenSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun opensAllFiveScreensAndProfileEditorWithoutUsagePermission() {
        compose.waitUntil(20_000) { compose.onAllNodesWithText("A little more headroom.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Navigate to Protected Apps").performClick()
        compose.onNodeWithText("Edit profile & preferences").performClick()
        compose.onNodeWithText("Make this profile yours").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        capture("protected-apps")
        compose.onNodeWithText("Add apps").performClick()
        compose.onNodeWithText("Choose your apps").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Navigate to Optimise").performClick()
        compose.onNodeWithText("Small changes. Real help.").assertIsDisplayed()
        capture("optimise")
        compose.onNodeWithContentDescription("Navigate to Diagnostics").performClick()
        compose.onNodeWithText("Export diagnostic report").assertExists()
        capture("diagnostics")
        compose.onNodeWithContentDescription("Navigate to Settings").performClick()
        compose.onNodeWithText("Made to stay light.").assertIsDisplayed()
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
        android.os.SystemClock.sleep(6_000)
        assertEquals(count, model.state.value.samples.size)
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
}
