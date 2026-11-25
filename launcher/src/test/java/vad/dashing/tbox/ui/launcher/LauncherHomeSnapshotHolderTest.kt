package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherHomeSnapshotHolderTest {

    @Test
    fun isSnapshotValidForTheme_matchesCurrentThemeOnly() {
        assertTrue(
            LauncherHomeSnapshotHolder.isSnapshotValidForTheme(
                hasBitmap = true,
                snapshotDarkTheme = true,
                currentDarkTheme = true,
            ),
        )
        assertTrue(
            LauncherHomeSnapshotHolder.isSnapshotValidForTheme(
                hasBitmap = true,
                snapshotDarkTheme = false,
                currentDarkTheme = false,
            ),
        )
        assertFalse(
            LauncherHomeSnapshotHolder.isSnapshotValidForTheme(
                hasBitmap = true,
                snapshotDarkTheme = false,
                currentDarkTheme = true,
            ),
        )
        assertFalse(
            LauncherHomeSnapshotHolder.isSnapshotValidForTheme(
                hasBitmap = true,
                snapshotDarkTheme = true,
                currentDarkTheme = false,
            ),
        )
        assertFalse(
            LauncherHomeSnapshotHolder.isSnapshotValidForTheme(
                hasBitmap = false,
                snapshotDarkTheme = true,
                currentDarkTheme = true,
            ),
        )
        assertFalse(
            LauncherHomeSnapshotHolder.isSnapshotValidForTheme(
                hasBitmap = true,
                snapshotDarkTheme = null,
                currentDarkTheme = true,
            ),
        )
    }

    @Test
    fun canCaptureDesktopSnapshot_requiresCleanVisibleDesktop() {
        assertTrue(
            LauncherHomeSnapshotHolder.canCaptureDesktopSnapshot(
                avmActive = false,
                blankActivityShowing = false,
                vehicleSettingsOpen = false,
                firstDrawComplete = true,
                activityAlive = true,
                activityStarted = true,
            ),
        )
        assertFalse(
            LauncherHomeSnapshotHolder.canCaptureDesktopSnapshot(
                avmActive = true,
                blankActivityShowing = false,
                vehicleSettingsOpen = false,
                firstDrawComplete = true,
                activityAlive = true,
                activityStarted = true,
            ),
        )
        assertFalse(
            LauncherHomeSnapshotHolder.canCaptureDesktopSnapshot(
                avmActive = false,
                blankActivityShowing = true,
                vehicleSettingsOpen = false,
                firstDrawComplete = true,
                activityAlive = true,
                activityStarted = true,
            ),
        )
        assertFalse(
            LauncherHomeSnapshotHolder.canCaptureDesktopSnapshot(
                avmActive = false,
                blankActivityShowing = false,
                vehicleSettingsOpen = true,
                firstDrawComplete = true,
                activityAlive = true,
                activityStarted = true,
            ),
        )
        assertFalse(
            LauncherHomeSnapshotHolder.canCaptureDesktopSnapshot(
                avmActive = false,
                blankActivityShowing = false,
                vehicleSettingsOpen = false,
                firstDrawComplete = false,
                activityAlive = true,
                activityStarted = true,
            ),
        )
        assertFalse(
            LauncherHomeSnapshotHolder.canCaptureDesktopSnapshot(
                avmActive = false,
                blankActivityShowing = false,
                vehicleSettingsOpen = false,
                firstDrawComplete = true,
                activityAlive = false,
                activityStarted = true,
            ),
        )
        assertFalse(
            LauncherHomeSnapshotHolder.canCaptureDesktopSnapshot(
                avmActive = false,
                blankActivityShowing = false,
                vehicleSettingsOpen = false,
                firstDrawComplete = true,
                activityAlive = true,
                activityStarted = false,
            ),
        )
    }

    @Test
    fun computeRemainingRefreshDelayMs_respectsTwoSecondWindowAndMinDelay() {
        assertEquals(
            1_800L,
            LauncherHomeSnapshotHolder.computeRemainingRefreshDelayMs(
                lastThemeChangeElapsedMs = 10_000L,
                nowElapsedMs = 10_200L,
                minDelayMs = LauncherHomeSnapshotHolder.SETTINGS_CLOSE_CAPTURE_DELAY_MS,
            ),
        )
        assertEquals(
            LauncherHomeSnapshotHolder.SETTINGS_CLOSE_CAPTURE_DELAY_MS,
            LauncherHomeSnapshotHolder.computeRemainingRefreshDelayMs(
                lastThemeChangeElapsedMs = 10_000L,
                nowElapsedMs = 15_000L,
                minDelayMs = LauncherHomeSnapshotHolder.SETTINGS_CLOSE_CAPTURE_DELAY_MS,
            ),
        )
        assertEquals(
            LauncherHomeSnapshotHolder.THEME_CHANGE_CAPTURE_DELAY_MS,
            LauncherHomeSnapshotHolder.computeRemainingRefreshDelayMs(
                lastThemeChangeElapsedMs = 10_000L,
                nowElapsedMs = 40_000L,
                minDelayMs = LauncherHomeSnapshotHolder.THEME_CHANGE_CAPTURE_DELAY_MS,
            ),
        )
        assertEquals(
            LauncherHomeSnapshotHolder.THEME_CHANGE_CAPTURE_DELAY_MS,
            LauncherHomeSnapshotHolder.computeRemainingRefreshDelayMs(
                lastThemeChangeElapsedMs = 10_000L,
                nowElapsedMs = 9_500L,
                minDelayMs = LauncherHomeSnapshotHolder.SETTINGS_CLOSE_CAPTURE_DELAY_MS,
            ),
        )
    }
}
