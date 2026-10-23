package dev.hamweb.stayopen.domain

import org.junit.Assert.*
import org.junit.Test

class RamClearPolicyTest {
    private fun process(pid: Int = 10, uid: Int = 10_123, importance: Int = 400, vararg names: String) =
        CachedProcess(pid, uid, importance, names.toSet().ifEmpty { setOf("example.game") })

    @Test fun explicitlySelectedCachedUserAppIsEligible() {
        assertEquals(listOf(RamCandidate("example.game", listOf(10))),
            RamClearPolicy.candidates(listOf(process()), setOf("example.game"), emptySet(), 0))
    }
    @Test fun emptySelectionCannotKillAnything() {
        assertTrue(RamClearPolicy.candidates(listOf(process()), emptySet(), emptySet(), 0).isEmpty())
    }
    @Test fun protectedAppIsExcludedEvenIfSelectedForClearing() {
        assertTrue(RamClearPolicy.candidates(listOf(process()), setOf("example.game"), setOf("example.game"), 0).isEmpty())
    }
    @Test fun foregroundVisibleAndServiceProcessesAreExcluded() {
        for (importance in listOf(100, 125, 200, 230, 300, 325, 350, 399))
            assertTrue(RamClearPolicy.candidates(listOf(process(importance = importance)), setOf("example.game"), emptySet(), 0).isEmpty())
    }
    @Test fun activeSecondaryProcessBlocksWholeUid() {
        assertTrue(RamClearPolicy.candidates(listOf(process(), process(pid = 11, importance = 100)),
            setOf("example.game"), emptySet(), 0).isEmpty())
    }
    @Test fun sharedUidRequiresEveryPackageSelectedAndNoneProtected() {
        val shared = listOf(process(names = arrayOf("example.game", "example.chat")))
        assertTrue(RamClearPolicy.candidates(shared, setOf("example.game"), emptySet(), 0).isEmpty())
        assertTrue(RamClearPolicy.candidates(shared, setOf("example.game", "example.chat"), setOf("example.chat"), 0).isEmpty())
        assertEquals(1, RamClearPolicy.candidates(shared, setOf("example.game", "example.chat"), emptySet(), 0).size)
    }
    @Test fun systemUidAndOtherUsersAreExcluded() {
        for (uid in listOf(0, 1000, 2000, 9999, 110_123))
            assertTrue(RamClearPolicy.candidates(listOf(process(uid = uid)), setOf("example.game"), emptySet(), 0).isEmpty())
    }
    @Test fun secondaryUserCanOnlyClearOwnApps() {
        val rows = listOf(process(uid = 110_123))
        assertEquals(1, RamClearPolicy.candidates(rows, setOf("example.game"), emptySet(), 1).size)
    }
    @Test fun commandsAndMalformedPackageNamesCannotBeCandidates() {
        for (name in listOf("example.game;am kill-all", "$(id)", "-a", "example app", "android", "../game")) {
            assertFalse(RamClearPolicy.validPackage(name))
            assertTrue(RamClearPolicy.candidates(listOf(process(names = arrayOf(name))), setOf(name), emptySet(), 0).isEmpty())
        }
    }
    @Test fun pressureNeedsTwoReadingsAndCooldown() {
        assertFalse(RamClearPolicy.shouldRun(1, 1000, null))
        assertTrue(RamClearPolicy.shouldRun(2, 1000, null))
        assertFalse(RamClearPolicy.shouldRun(10, 301_000, 1001))
        assertTrue(RamClearPolicy.shouldRun(2, 301_001, 1001))
    }
    @Test fun androidThresholdAndLowMemoryFlagOverrideLowerUserThreshold() {
        assertTrue(RamClearPolicy.underPressure(MemorySample(0, 1000, 180, 200, false), 10))
        assertTrue(RamClearPolicy.underPressure(MemorySample(0, 1000, 500, 50, true), 10))
        assertTrue(RamClearPolicy.underPressure(MemorySample(0, 1000, 150, 50, false), 15))
        assertFalse(RamClearPolicy.underPressure(MemorySample(0, 1000, 151, 50, false), 15))
        assertFalse(RamClearPolicy.underPressure(MemorySample(0, 0, 0, 0, true), 15))
    }
    @Test fun oneRunHasABoundedNumberOfAppRequests() {
        val rows = (0..30).map { process(pid = it + 1, uid = 10_000 + it, names = arrayOf("example.app$it")) }
        assertEquals(RamClearPolicy.MAX_APPS_PER_RUN, RamClearPolicy.candidates(rows, rows.flatMap { it.packages }.toSet(), emptySet(), 0).size)
    }
}
