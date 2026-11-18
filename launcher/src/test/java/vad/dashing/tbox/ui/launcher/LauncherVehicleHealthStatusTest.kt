package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherVehicleHealthStatusTest {

    @Test
    fun computeVehicleHealthStatus_allNormalReturnsOk() {
        val status = computeVehicleHealthStatus(
            alerts = emptyList(),
            hasTireAttention = false,
            srrSystem = LauncherSrrSystemState.Hidden,
            accMode = LauncherAdasAccMode.Off,
            simShowAll = false,
        )
        assertEquals(LauncherVehicleHealthStatus.Ok, status)
    }

    @Test
    fun computeVehicleHealthStatus_accFaultReturnsWarning() {
        val status = computeVehicleHealthStatus(
            alerts = emptyList(),
            hasTireAttention = false,
            srrSystem = LauncherSrrSystemState.Hidden,
            accMode = LauncherAdasAccMode.Fault,
            simShowAll = false,
        )
        assertEquals(LauncherVehicleHealthStatus.Warning, status)
    }

    @Test
    fun computeVehicleHealthStatus_warningAlertReturnsWarning() {
        val status = computeVehicleHealthStatus(
            alerts = listOf(LauncherVehicleAlert(LauncherAlertId.LowFuel, LauncherAlertSeverity.Warning)),
            hasTireAttention = false,
            srrSystem = LauncherSrrSystemState.Hidden,
            accMode = LauncherAdasAccMode.Off,
            simShowAll = false,
        )
        assertEquals(LauncherVehicleHealthStatus.Warning, status)
    }

    @Test
    fun computeVehicleHealthStatus_criticalAlertReturnsError() {
        val status = computeVehicleHealthStatus(
            alerts = listOf(LauncherVehicleAlert(LauncherAlertId.SeatBeltDriver, LauncherAlertSeverity.Critical)),
            hasTireAttention = false,
            srrSystem = LauncherSrrSystemState.Hidden,
            accMode = LauncherAdasAccMode.Fault,
            simShowAll = false,
        )
        assertEquals(LauncherVehicleHealthStatus.Error, status)
    }

    @Test
    fun computeVehicleHealthStatus_tireAttentionReturnsError() {
        val status = computeVehicleHealthStatus(
            alerts = emptyList(),
            hasTireAttention = true,
            srrSystem = LauncherSrrSystemState.Hidden,
            accMode = LauncherAdasAccMode.Fault,
            simShowAll = false,
        )
        assertEquals(LauncherVehicleHealthStatus.Error, status)
    }

    @Test
    fun computeVehicleHealthStatus_srrFaultReturnsError() {
        val status = computeVehicleHealthStatus(
            alerts = emptyList(),
            hasTireAttention = false,
            srrSystem = LauncherSrrSystemState.Fault,
            accMode = LauncherAdasAccMode.Fault,
            simShowAll = false,
        )
        assertEquals(LauncherVehicleHealthStatus.Error, status)
    }

    @Test
    fun computeVehicleHealthStatus_simShowAllReturnsError() {
        val status = computeVehicleHealthStatus(
            alerts = emptyList(),
            hasTireAttention = false,
            srrSystem = LauncherSrrSystemState.Hidden,
            accMode = LauncherAdasAccMode.Off,
            simShowAll = true,
        )
        assertEquals(LauncherVehicleHealthStatus.Error, status)
    }
}
