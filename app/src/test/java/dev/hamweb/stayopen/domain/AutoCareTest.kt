package dev.hamweb.stayopen.domain

import org.junit.Assert.*
import org.junit.Test

class AutoCareTest {
    @Test fun disabledCareNeverRunsEvenDuringHighPressure() {
        assertNull(AutoCarePolicy.reason(false, 10_000_000, null, null, Pressure.HIGH))
    }
    @Test fun firstVisibleSampleCanStartAutomaticCare() {
        assertEquals(CareReason.START, AutoCarePolicy.reason(true, 100, null, null, Pressure.NORMAL))
    }
    @Test fun repeatedHighReadingsDoNotCreateCareLoops() {
        assertNull(AutoCarePolicy.reason(true, 10_000, 0, Pressure.NORMAL, Pressure.HIGH))
        assertNull(AutoCarePolicy.reason(true, 130_000, 0, Pressure.HIGH, Pressure.HIGH))
    }
    @Test fun aNewHighPressureEdgeRespectsTheCooldown() {
        assertEquals(CareReason.PRESSURE, AutoCarePolicy.reason(true, 120_000, 0, Pressure.ELEVATED, Pressure.HIGH))
    }
    @Test fun housekeepingIntervalIsFifteenMinutesOfElapsedTime() {
        assertNull(AutoCarePolicy.reason(true, 899_999, 0, Pressure.NORMAL, Pressure.NORMAL))
        assertEquals(CareReason.INTERVAL, AutoCarePolicy.reason(true, 900_000, 0, Pressure.NORMAL, Pressure.NORMAL))
    }
    @Test fun backwardsClockValueDoesNotCauseARunLoop() {
        assertNull(AutoCarePolicy.reason(true, 1, 50, Pressure.NORMAL, Pressure.HIGH))
    }
    @Test fun highPressureBackoffReducesOwnSamplingInsteadOfKillingApps() {
        assertEquals(10, AutoCarePolicy.sampleInterval(5, true, Pressure.HIGH))
        assertEquals(30, AutoCarePolicy.sampleInterval(30, true, Pressure.HIGH))
        assertEquals(5, AutoCarePolicy.sampleInterval(5, false, Pressure.HIGH))
        assertEquals(5, AutoCarePolicy.sampleInterval(5, true, Pressure.NORMAL))
    }
    @Test fun memorySummaryUsesOnlyTheProvidedMeasuredSamples() {
        assertNull(summariseMemory(emptyList()))
        val samples = listOf(100L, 300L, 500L).map { MemorySample(it, 1000, it, 50, false) }
        val summary = summariseMemory(samples)!!
        assertEquals(100, summary.minimumAvailable)
        assertEquals(300, summary.averageAvailable)
        assertEquals(500, summary.maximumAvailable)
        assertEquals(3, summary.count)
    }
}
