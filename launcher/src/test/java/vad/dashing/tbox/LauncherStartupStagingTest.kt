package vad.dashing.tbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherStartupStagingTest {

    @Test
    fun testFirstDrawCompleteState() {
        LauncherHomeActivityHolder.isFirstDrawComplete = false
        assertFalse(LauncherHomeActivityHolder.isFirstDrawComplete)
        assertFalse(LauncherHomeActivityHolder.firstDrawFlow.value)

        LauncherHomeActivityHolder.isFirstDrawComplete = true
        assertTrue(LauncherHomeActivityHolder.isFirstDrawComplete)
        assertTrue(LauncherHomeActivityHolder.firstDrawFlow.value)

        LauncherHomeActivityHolder.isFirstDrawComplete = false
        assertFalse(LauncherHomeActivityHolder.isFirstDrawComplete)
        assertFalse(LauncherHomeActivityHolder.firstDrawFlow.value)
    }

    @Test
    fun testLauncherHomeActivityHolderResumedState() {
        LauncherHomeActivityHolder.isResumed = false
        assertFalse(LauncherHomeActivityHolder.isResumed)
        assertFalse(LauncherHomeActivityHolder.isResumedFlow.value)

        LauncherHomeActivityHolder.isResumed = true
        assertTrue(LauncherHomeActivityHolder.isResumed)
        assertTrue(LauncherHomeActivityHolder.isResumedFlow.value)

        LauncherHomeActivityHolder.isResumed = false
        assertFalse(LauncherHomeActivityHolder.isResumed)
        assertFalse(LauncherHomeActivityHolder.isResumedFlow.value)
    }

    @Test
    fun testVehicleControlRepositoryConsumerLifecycle() {
        vad.dashing.tbox.ui.launcher.LauncherVehicleControlRepository.registerConsumer(includeExperimental = false)
        vad.dashing.tbox.ui.launcher.LauncherVehicleControlRepository.registerConsumer(includeExperimental = true)
        vad.dashing.tbox.ui.launcher.LauncherVehicleControlRepository.refreshNow()
        vad.dashing.tbox.ui.launcher.LauncherVehicleControlRepository.unregisterConsumer(includeExperimental = true)
        vad.dashing.tbox.ui.launcher.LauncherVehicleControlRepository.unregisterConsumer(includeExperimental = false)
    }
}
