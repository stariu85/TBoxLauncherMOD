package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HuRebootAfterUpdateTest {

    @Test
    fun firstInstallDoesNotRecommendReboot() {
        val step = HuRebootAfterUpdate.onAppStart(
            currentVersionCode = 52L,
            elapsedRealtimeMs = 10_000L,
            lastUpdateTimeMs = 1_000L,
            firstInstallTimeMs = 1_000L,
            nowWallClockMs = 2_000L,
            previous = HuRebootAfterUpdateSnapshot(),
        )
        assertEquals(52L, step.snapshot.lastSeenVersionCode)
        assertFalse(step.ui.recommendReboot)
        assertFalse(step.ui.shouldOpenStartupPrompt)
    }

    @Test
    fun recentUpdateWithNoHistoryRecommendsReboot() {
        val step = HuRebootAfterUpdate.onAppStart(
            currentVersionCode = 53L,
            elapsedRealtimeMs = 8_000L,
            lastUpdateTimeMs = 1_000L,
            firstInstallTimeMs = 100L,
            nowWallClockMs = 1_000L + 60_000L,
            previous = HuRebootAfterUpdateSnapshot(),
        )
        assertTrue(step.ui.recommendReboot)
        assertTrue(step.ui.shouldOpenStartupPrompt)
        assertEquals(53L, step.snapshot.pendingRebootVersionCode)
    }

    @Test
    fun versionBumpRecommendsRebootOnceUntilShown() {
        val afterBump = HuRebootAfterUpdate.onAppStart(
            currentVersionCode = 53L,
            elapsedRealtimeMs = 20_000L,
            lastUpdateTimeMs = 9_000L,
            firstInstallTimeMs = 1_000L,
            nowWallClockMs = 10_000L,
            previous = HuRebootAfterUpdateSnapshot(lastSeenVersionCode = 52L),
        )
        assertTrue(afterBump.ui.shouldOpenStartupPrompt)

        val shown = HuRebootAfterUpdate.markStartupPromptShown(afterBump.snapshot, 53L)
        val ui = HuRebootAfterUpdate.uiState(shown, 53L)
        assertTrue(ui.recommendReboot)
        assertFalse(ui.shouldOpenStartupPrompt)
    }

    @Test
    fun sameVersionStartDoesNotReopenPromptAfterShown() {
        val previous = HuRebootAfterUpdateSnapshot(
            lastSeenVersionCode = 53L,
            pendingRebootVersionCode = 53L,
            upgradeDetectedElapsedMs = 5_000L,
            startupPromptShownForVersion = 53L,
        )
        val step = HuRebootAfterUpdate.onAppStart(
            currentVersionCode = 53L,
            elapsedRealtimeMs = 40_000L,
            lastUpdateTimeMs = 9_000L,
            firstInstallTimeMs = 1_000L,
            nowWallClockMs = 50_000L,
            previous = previous,
        )
        assertTrue(step.ui.recommendReboot)
        assertFalse(step.ui.shouldOpenStartupPrompt)
    }

    @Test
    fun elapsedRealtimeResetClearsPendingAfterHuReboot() {
        val previous = HuRebootAfterUpdateSnapshot(
            lastSeenVersionCode = 53L,
            pendingRebootVersionCode = 53L,
            upgradeDetectedElapsedMs = 90_000L,
            startupPromptShownForVersion = 53L,
        )
        val step = HuRebootAfterUpdate.onAppStart(
            currentVersionCode = 53L,
            elapsedRealtimeMs = 3_000L,
            lastUpdateTimeMs = 9_000L,
            firstInstallTimeMs = 1_000L,
            nowWallClockMs = 100_000L,
            previous = previous,
        )
        assertEquals(0L, step.snapshot.pendingRebootVersionCode)
        assertFalse(step.ui.recommendReboot)
        assertFalse(step.ui.shouldOpenStartupPrompt)
    }

    @Test
    fun awaitingInstallMarksUpgradeWhenNewVersionStarts() {
        val awaiting = HuRebootAfterUpdate.markAwaitingInstall(
            HuRebootAfterUpdateSnapshot(lastSeenVersionCode = 52L),
            installVersionCode = 53L,
        )
        val step = HuRebootAfterUpdate.onAppStart(
            currentVersionCode = 53L,
            elapsedRealtimeMs = 4_000L,
            lastUpdateTimeMs = 20_000L,
            firstInstallTimeMs = 1_000L,
            nowWallClockMs = 21_000L,
            previous = awaiting,
        )
        assertEquals(0L, step.snapshot.awaitingInstallVersionCode)
        assertTrue(step.ui.recommendReboot)
    }

    @Test
    fun markRebootedStopsRecommendation() {
        val pending = HuRebootAfterUpdateSnapshot(
            lastSeenVersionCode = 53L,
            pendingRebootVersionCode = 53L,
            upgradeDetectedElapsedMs = 10_000L,
        )
        val after = HuRebootAfterUpdate.markRebooted(pending, 53L)
        val ui = HuRebootAfterUpdate.uiState(after, 53L)
        assertFalse(ui.recommendReboot)
        assertFalse(ui.shouldOpenStartupPrompt)
    }

    @Test
    fun bootIdChangeClearsPendingEvenWhenElapsedIsHigher() {
        val previous = HuRebootAfterUpdateSnapshot(
            lastSeenVersionCode = 53L,
            pendingRebootVersionCode = 53L,
            upgradeDetectedElapsedMs = 5_000L,
            startupPromptShownForVersion = 53L,
            upgradeDetectedBootId = "boot-uuid-1",
        )
        val step = HuRebootAfterUpdate.onAppStart(
            currentVersionCode = 53L,
            elapsedRealtimeMs = 120_000L,
            lastUpdateTimeMs = 9_000L,
            firstInstallTimeMs = 1_000L,
            nowWallClockMs = 150_000L,
            previous = previous,
            currentBootId = "boot-uuid-2",
        )
        assertEquals(0L, step.snapshot.pendingRebootVersionCode)
        assertFalse(step.ui.recommendReboot)
    }

    @Test
    fun sameBootIdKeepsPendingRecommendation() {
        val previous = HuRebootAfterUpdateSnapshot(
            lastSeenVersionCode = 53L,
            pendingRebootVersionCode = 53L,
            upgradeDetectedElapsedMs = 5_000L,
            startupPromptShownForVersion = 53L,
            upgradeDetectedBootId = "boot-uuid-1",
        )
        val step = HuRebootAfterUpdate.onAppStart(
            currentVersionCode = 53L,
            elapsedRealtimeMs = 40_000L,
            lastUpdateTimeMs = 9_000L,
            firstInstallTimeMs = 1_000L,
            nowWallClockMs = 50_000L,
            previous = previous,
            currentBootId = "boot-uuid-1",
        )
        assertTrue(step.ui.recommendReboot)
        assertFalse(step.ui.shouldOpenStartupPrompt)
    }
}
