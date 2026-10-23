package dev.hamweb.stayopen

import android.app.ActivityManager
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.hamweb.stayopen.data.PreferenceStore
import dev.hamweb.stayopen.domain.BridgeAccess
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShizukuIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use { String(it.readBytes()).trim() }

    @Test fun permissionGrantRealClearProtectedExclusionAndBackgroundService() {
        assumeTrue("Only enabled on the disposable CI emulator with Shizuku installed",
            InstrumentationRegistry.getArguments().getString("privilegedTests") == "true")
        val fixture = "dev.hamweb.stayopen.ramfixture"
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        lateinit var model: StayOpenViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[StayOpenViewModel::class.java] }
        compose.waitUntil(20_000) { model.state.value.ready && model.state.value.apps.any { it.packageName == fixture } }
        val original = model.state.value.preferences
        val controller = model.ramController
        compose.runOnIdle { controller.refreshAccess() }
        compose.waitUntil(15_000) { controller.state.value.access == BridgeAccess.PERMISSION || controller.state.value.access == BridgeAccess.READY }
        if (controller.state.value.access != BridgeAccess.READY) {
            compose.runOnIdle { controller.requestPermission() }
            val allow = device.wait(Until.findObject(By.textContains("Allow all")), 15_000)
                ?: device.wait(Until.findObject(By.text("Allow")), 2_000)
            assertNotNull("Shizuku's genuine permission dialog did not appear", allow)
            allow!!.click()
        }
        compose.waitUntil(15_000) { controller.state.value.access == BridgeAccess.READY }
        assertTrue(controller.state.value.detail.contains("ADB / shell"))
        try {
            compose.runOnIdle { if (fixture !in model.state.value.preferences.ramClearApps) model.toggleRamClearApp(fixture) }
            shell("am start -W -n $fixture/.MemoryFixtureActivity")
            val pid = shell("pidof $fixture")
            assertTrue(pid.isNotBlank())

            fun clear(): dev.hamweb.stayopen.domain.RamRun {
                val count = controller.state.value.runs.size
                InstrumentationRegistry.getInstrumentation().runOnMainSync { controller.clearNow() }
                compose.waitUntil(25_000) { controller.state.value.runs.size > count || controller.state.value.error != null }
                assertNull(controller.state.value.error)
                return controller.state.value.runs.last()
            }
            // The active fixture is selected but must not be targeted.
            assertEquals(0, clear().requested)
            assertEquals(pid, shell("pidof $fixture"))
            device.pressHome()
            var cached = false
            repeat(25) {
                if (!cached) {
                    InstrumentationRegistry.getInstrumentation().runOnMainSync { controller.scan() }
                    compose.waitUntil(20_000) { !controller.state.value.busy }
                    cached = controller.state.value.candidates.orEmpty().any { it.packageName == fixture }
                    if (!cached) android.os.SystemClock.sleep(500)
                }
            }
            assertTrue("Real fixture did not reach cached state", cached)

            // Protect through the real profile UI model; the same cached process must survive.
            InstrumentationRegistry.getInstrumentation().runOnMainSync { model.toggleApp(fixture) }
            assertEquals(0, clear().requested)
            assertEquals(pid, shell("pidof $fixture"))
            InstrumentationRegistry.getInstrumentation().runOnMainSync { model.toggleApp(fixture) }
            val run = clear()
            assertTrue("The selected cached process did not exit", run.exited >= 1)
            assertFalse(shell("pidof $fixture").split(' ').contains(pid))
            assertTrue(run.beforeBytes > 0 && run.afterBytes > 0)

            shell("am start -W -n ${compose.activity.packageName}/.MainActivity")
            compose.waitUntil(15_000) { runCatching { compose.onAllNodesWithText("SYSTEM MONITOR").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
            compose.onNodeWithContentDescription("Navigate to Optimise").performClick()
            compose.onNodeWithText("PRIVILEGED ACCESS / GRANTED").assertExists()
            shell("mkdir -p /sdcard/Download/StayOpen-test-screenshots")
            shell("screencap -p /sdcard/Download/StayOpen-test-screenshots/ram-access-granted.png")
            shell("pm grant ${compose.activity.packageName} android.permission.POST_NOTIFICATIONS")
            compose.runOnIdle { model.startRamAutomation() }
            compose.waitUntil(10_000) { controller.state.value.automatic && controller.state.value.lastAvailableBytes != null }
            device.pressHome()
            android.os.SystemClock.sleep(1_000)
            assertTrue("Opt-in automatic service stopped when UI was hidden", controller.state.value.automatic)
            assertTrue(shell("dumpsys activity services ${compose.activity.packageName}").contains("isForeground=true"))
            InstrumentationRegistry.getInstrumentation().runOnMainSync { controller.stopAutomatic() }
            compose.waitUntil(10_000) { !controller.state.value.automatic }
            @Suppress("DEPRECATION")
            compose.waitUntil(10_000) { compose.activity.getSystemService(ActivityManager::class.java).getRunningServices(100).none { it.service.className.endsWith("RamMonitorService") } }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                controller.stopAutomatic()
                if ((fixture in model.state.value.preferences.activeProfile.packages) != (fixture in original.activeProfile.packages)) model.toggleApp(fixture)
                if ((fixture in model.state.value.preferences.ramClearApps) != (fixture in original.ramClearApps)) model.toggleRamClearApp(fixture)
            }
            compose.waitUntil(10_000) { PreferenceStore(compose.activity).load() == original }
            shell("am start -W -n ${compose.activity.packageName}/.MainActivity")
        }
    }
}
