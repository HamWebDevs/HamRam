package dev.hamweb.stayopen.data

import dev.hamweb.stayopen.domain.*
import org.junit.Assert.*
import org.junit.Test

class ReportExporterTest {
    @Test fun reportKeepsMeasuredFactsAndUnverifiableClaimsSeparate() {
        val sample = MemorySample(1, 8 * GIB, GIB, GIB / 2, false)
        val snapshot = Snapshot(sample, 128 * GIB, 32 * GIB, false, false, 0, 20_000_000)
        val report = ReportExporter.create("test", DeviceInfo("Samsung", "SM-G986B", "13", 33), snapshot,
            Preferences(), listOf(AppEntry("example.app", "Example app")), listOf(AppRestriction("example.app", false)),
            listOf(sample), emptyList(), emptyList(), emptyList())
        assertTrue(report.contains("Available RAM: 1.00 GiB (1073741824 bytes)"))
        assertTrue(report.contains("Estimated pressure: Elevated"))
        assertTrue(report.contains("Doze exemption=false"))
        assertTrue(report.contains("cannot be verified"))
        assertTrue(report.contains("OPTIONAL APP USAGE · disabled"))
        assertTrue(report.contains("No account, serial number, IMEI or phone number is included."))
    }
    @Test fun reportDoesNotInventMissingSnapshotOrObservations() {
        val report = ReportExporter.create("test", DeviceInfo("Google", "Pixel", "15", 35), null,
            Preferences(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue(report.contains("No system snapshot has been collected."))
        assertTrue(report.contains("LOW-MEMORY OBSERVATIONS · 0"))
        assertFalse(report.contains("Available RAM:"))
    }
}
