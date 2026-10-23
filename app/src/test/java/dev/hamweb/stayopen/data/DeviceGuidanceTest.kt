package dev.hamweb.stayopen.data

import dev.hamweb.stayopen.domain.OneUi
import org.junit.Assert.*
import org.junit.Test

class DeviceGuidanceTest {
    private fun device(model: String, sdk: Int) = DeviceInfo("samsung", model, sdk.toString(), sdk)
    @Test fun targetModelsAreRecognisedWithoutAssumingTheirRamCapacity() {
        assertEquals("Samsung Galaxy S20+", device("SM-G986B", 33).friendlyModel)
        assertEquals("Samsung Galaxy A17", device("SM-A176B", 35).friendlyModel)
        assertEquals("Samsung Galaxy A14", device("SM-A145R", 33).friendlyModel)
        assertEquals("Samsung Galaxy A14", device("SM-A146P", 35).friendlyModel)
        assertFalse(device("SM-A165F", 35).friendlyModel.endsWith("A17"))
    }
    @Test fun menuPathsUseAndroidFallbackAndExplicitOneUiChoice() {
        assertTrue(device("SM-G986B", 33).sleepingPath(OneUi.AUTO).contains("Battery and device care"))
        assertEquals("Settings → Battery → Background usage limits", device("SM-A176B", 35).sleepingPath(OneUi.AUTO))
        assertTrue(device("SM-A176B", 35).sleepingPath(OneUi.V5).contains("Battery and device care"))
        assertFalse(device("SM-G986B", 33).ramPlusPath(OneUi.V7).contains("Battery and device care"))
    }
    @Test fun nonSamsungModelKeepsManufacturerVisible() {
        val pixel = DeviceInfo("Google", "Pixel 8", "15", 35)
        assertFalse(pixel.isSamsung)
        assertEquals("Google Pixel 8", pixel.friendlyModel)
    }
}
