package dev.hamweb.stayopen.domain

import org.junit.Assert.*
import org.junit.Test

class DiagnosticsEngineTest {
    private fun sample(available: Long = 4 * GIB, threshold: Long = GIB / 2, low: Boolean = false) =
        MemorySample(1L, 8 * GIB, available, threshold, low)
    private fun snapshot(memory: MemorySample = sample(), storage: Long = 32 * GIB, saver: Boolean = false, thermal: Int? = 0) =
        Snapshot(memory, 128 * GIB, storage, saver, false, thermal, 24_000_000)

    @Test fun systemLowMemoryOverridesAvailableMemoryHeuristic() {
        assertEquals(Pressure.HIGH, estimatePressure(sample(low = true)))
    }
    @Test fun pressureUsesSystemThresholdAndHeadroomInsteadOfUsedPercentAlone() {
        assertEquals(Pressure.HIGH, estimatePressure(sample(GIB / 2)))
        assertEquals(Pressure.ELEVATED, estimatePressure(sample(GIB)))
        assertEquals(Pressure.NORMAL, estimatePressure(sample(2 * GIB)))
        assertEquals(Pressure.NORMAL, estimatePressure(sample(GIB, GIB / 8).copy(totalBytes = 4 * GIB)))
    }
    @Test fun zeroUnknownThresholdDoesNotItselfTriggerHighPressure() {
        assertEquals(Pressure.NORMAL, estimatePressure(sample(4 * GIB, 0)))
        assertEquals(Pressure.ELEVATED, estimatePressure(sample(0, 0)))
    }
    @Test fun storageAndSevereThermalWarningsRankBeforeGenericGuidance() {
        val findings = DiagnosticsEngine.findings(snapshot(storage = GIB, thermal = 3), emptyList(), true)
        assertEquals("storage", findings[0].id)
        assertEquals("thermal", findings[1].id)
        assertEquals(Evidence.MEASURED, findings[0].evidence)
        assertEquals(Evidence.GUIDANCE, findings.first { it.id == "sleep" }.evidence)
        assertEquals("Needs attention", DiagnosticsEngine.headline(findings))
    }
    @Test fun normalSnapshotIsNotPresentedAsAProblem() {
        val findings = DiagnosticsEngine.findings(snapshot(), emptyList(), false)
        assertTrue(findings.none { it.evidence == Evidence.MEASURED })
        assertEquals("No current warning signs", DiagnosticsEngine.headline(findings))
    }
    @Test fun knownNonExemptAppsAreDistinguishedFromUnavailableStatus() {
        val findings = DiagnosticsEngine.findings(snapshot(), listOf(
            AppRestriction("example.unknown", null), AppRestriction("example.allowed", true), AppRestriction("example.default", false)), false)
        assertTrue(findings.first { it.id == "exemption" }.title.startsWith("1 "))
    }
    @Test fun profilesStartWithIndependentEmptySelectionsAndGentleIntervals() {
        val p = Preferences()
        assertEquals(5, p.profiles.size)
        assertEquals("Everyday Use", p.activeProfile.name)
        val gaming = p.profiles.first { it.id == "gaming" }.copy(packages = setOf("example.game"))
        assertTrue(p.activeProfile.packages.isEmpty())
        assertTrue(gaming.packages.isNotEmpty())
        assertTrue(p.profiles.all { it.intervalSeconds >= 5 })
    }
    @Test fun invalidActiveProfileFallsBackWithoutLosingStoredProfiles() {
        assertEquals("everyday", Preferences(activeProfileId = "removed").activeProfile.id)
    }
}
