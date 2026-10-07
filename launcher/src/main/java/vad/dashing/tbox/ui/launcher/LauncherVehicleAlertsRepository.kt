package vad.dashing.tbox.ui.launcher

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.mbcan.MbCanAvailability
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.mbcan.VehicleBodyState
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/**
 * Aggregates OEM-style vehicle reminders for the launcher:
 * seat belts (chime / cached seat-belt status), doors/hood/trunk, ICM fault flags, TPMS lamp.
 */
object LauncherVehicleAlertsRepository {
    private const val ENGINE_CLASS = "com.mengbo.mbCan.MBCanEngine"
    private const val POLL_MS = 1_200L
    /** After a live chime/seat push, ignore stale cache clears for a short window. */
    private const val BELT_PUSH_STICKY_MS = 3_500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null
    private var chimeProxy: Any? = null
    private var seatBeltProxy: Any? = null
    private var active = false
    private var beltStickyUntilElapsedMs = 0L

    private var driverBelt = false
    private var passengerBelt = false
    private var rearLeftBelt = false
    private var rearMidBelt = false
    private var rearRightBelt = false

    private var pressBrake = false
    private var hvFaultStop = false
    private var chargeFault = false
    private var sysFault = false
    private var battFault = false
    private var lowSoc = false
    private var packThermal = false
    private var speeding = false
    private var highTemp = false
    private var lowFuel = false
    private var powerModeFail = false

    private val _state = MutableStateFlow(LauncherVehicleAlertsState())
    val state: StateFlow<LauncherVehicleAlertsState> = _state.asStateFlow()

    fun ensureActive() {
        if (active) return
        active = true
        registerChimeListener()
        registerSeatBeltListener()
        MbCanEngineFacade.subscribe(
            setOf(
                "eMBCAN_SEAT_BELT_STATUS",
                "eMBCAN_CHIME_STATUS",
                "eMBCAN_VEHICLE_ICM_FAULT_INFO",
            ),
        )
        pollJob = scope.launch {
            while (isActive) {
                pollSeatBeltCache()
                publish()
                delay(POLL_MS)
            }
        }
    }

    fun stop() {
        if (!active) return
        pollJob?.cancel()
        pollJob = null
        // Listener slots in the OEM engine are global. Keep our registered proxy instead of
        // calling unRegistIMBChimeStatusListener(), which can also remove another consumer.
        active = false
        beltStickyUntilElapsedMs = 0L
        clearSignals()
        _state.value = LauncherVehicleAlertsState()
    }

    fun applyIcmFaultInfo(fault: Any?) {
        if (fault == null) return
        pressBrake = byteActive(fault, "getICM_BrkPsd")
        hvFaultStop = byteActive(fault, "getICM_HvSysFltStopReq")
        chargeFault = byteActive(fault, "getICM_ChargeFault")
        sysFault = byteActive(fault, "getICM_SysFault")
        battFault = byteActive(fault, "getICM_BattFaultLampSts")
        lowSoc = byteActive(fault, "getICM_LowSOC") || byteActive(fault, "getICM_SOCDisp")
        packThermal = byteActive(fault, "getICM_PackThermalRunawayLight")
        speeding = byteActive(fault, "getICM_Speeding")
        highTemp = byteActive(fault, "getICM_HighTemperature")
        lowFuel = byteActive(fault, "getICM_Fuel")
        powerModeFail = byteActive(fault, "getICM_PowerModeChangeFail")
        publish()
    }

    /** Rebuild alert list (doors / TPMS / debug toggles may have changed). */
    fun refresh() {
        if (active) publish()
    }

    private fun registerChimeListener() {
        if (chimeProxy != null) return
        if (MbCanEngineFacade.ensureInitialized() !is MbCanAvailability.Available) return
        val inst = engineInstance() ?: return
        val iface = runCatching {
            Class.forName("com.mengbo.mbCan.interfaces.IMBCanChimeStatusCallback")
        }.getOrNull() ?: return
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _, method, args ->
            if (active && method.name == "onChimeStatusChange" && args?.isNotEmpty() == true) {
                applyChime(args[0])
            }
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
        runCatching {
            Class.forName(ENGINE_CLASS)
                .getMethod("registIMBChimeStatusListener", iface)
                .invoke(inst, proxy)
            chimeProxy = proxy
        }
    }

    private fun registerSeatBeltListener() {
        if (seatBeltProxy != null) return
        if (MbCanEngineFacade.ensureInitialized() !is MbCanAvailability.Available) return
        val inst = engineInstance() ?: return
        val iface = runCatching {
            Class.forName("com.mengbo.mbCan.interfaces.IMbCanSeatBeltStatusCallback")
        }.getOrNull() ?: return
        val register = inst.javaClass.methods.firstOrNull { method ->
            method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == iface &&
                method.name.contains("seat", ignoreCase = true)
        } ?: return
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _, method, args ->
            if (active && method.name == "onSeatBeltStatusChange" && args?.isNotEmpty() == true) {
                applyFrontSeatBelts(args[0])
            }
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
        runCatching {
            register.invoke(inst, proxy)
            seatBeltProxy = proxy
        }
    }

    private fun applyFrontSeatBelts(status: Any?) {
        if (status == null) return
        val d = byteActive(status, "getDriverWarning")
        val p = byteActive(status, "getPassengerWarning")
        driverBelt = d
        passengerBelt = p
        noteBeltPush(anyActive = d || p)
        publish()
    }

    private fun applyChime(chime: Any?) {
        if (chime == null) return
        val d = byteActive(chime, "getICM_2_DriverSeatBeltWarningSts")
        val p = byteActive(chime, "getICM_2_PassengerSeatBeltWarningSts")
        val rl = byteActive(chime, "getICM_2_RLSeatBeltWarningSts")
        val rm = byteActive(chime, "getICM_2_RMSeatBeltWarningSts")
        val rr = byteActive(chime, "getICM_2_RRSeatBeltWarningSts")
        driverBelt = d
        passengerBelt = p
        rearLeftBelt = rl
        rearMidBelt = rm
        rearRightBelt = rr
        noteBeltPush(anyActive = d || p || rl || rm || rr)
        publish()
    }

    /**
     * Seat-belt type-15 push dispatch in OEM engine is empty; cache via [getMbCanData] and chime
     * remain the sources of truth. A short sticky window prevents one stale zero poll from
     * clearing a fresh warning, without permanently disabling polling.
     */
    private fun pollSeatBeltCache() {
        val chime = readCachedChime()
        var d = driverBelt
        var p = passengerBelt
        var rl = rearLeftBelt
        var rm = rearMidBelt
        var rr = rearRightBelt
        if (chime != null) {
            d = byteActive(chime, "getICM_2_DriverSeatBeltWarningSts")
            p = byteActive(chime, "getICM_2_PassengerSeatBeltWarningSts")
            rl = byteActive(chime, "getICM_2_RLSeatBeltWarningSts")
            rm = byteActive(chime, "getICM_2_RMSeatBeltWarningSts")
            rr = byteActive(chime, "getICM_2_RRSeatBeltWarningSts")
        }
        readSeatBeltWarning()?.let { snap ->
            // Type 15 is authoritative for the two front seats.
            d = snap.first
            p = snap.second
        }
        val sticky = android.os.SystemClock.elapsedRealtime() < beltStickyUntilElapsedMs
        if (sticky) {
            // Only allow upgrades to "warning on" while sticky; never clear from stale cache.
            driverBelt = driverBelt || d
            passengerBelt = passengerBelt || p
            rearLeftBelt = rearLeftBelt || rl
            rearMidBelt = rearMidBelt || rm
            rearRightBelt = rearRightBelt || rr
        } else {
            driverBelt = d
            passengerBelt = p
            rearLeftBelt = rl
            rearMidBelt = rm
            rearRightBelt = rr
        }
    }

    private fun noteBeltPush(anyActive: Boolean) {
        if (anyActive) {
            beltStickyUntilElapsedMs =
                android.os.SystemClock.elapsedRealtime() + BELT_PUSH_STICKY_MS
        }
        vad.dashing.tbox.DiagFileLog.i(
            "SeatBelt",
            "push d=$driverBelt p=$passengerBelt rl=$rearLeftBelt rm=$rearMidBelt rr=$rearRightBelt stickyMs=$BELT_PUSH_STICKY_MS",
        )
    }

    private fun readCachedChime(): Any? {
        if (MbCanEngineFacade.ensureInitialized() !is MbCanAvailability.Available) return null
        return runCatching {
            val cls = Class.forName("com.mengbo.mbCan.entity.MBCanChime")
            MbCanEngineFacade.getMbCanData(18, cls)
        }.getOrNull()
    }

    private fun readSeatBeltWarning(): Pair<Boolean, Boolean>? {
        if (MbCanEngineFacade.ensureInitialized() !is MbCanAvailability.Available) return null
        return runCatching {
            val cls = Class.forName("com.mengbo.mbCan.entity.MBCanSeatBeltWarning")
            val obj = MbCanEngineFacade.getMbCanData(15, cls) ?: return null
            val d = (cls.getMethod("getDriverWarning").invoke(obj) as? Number)?.toInt() ?: 0
            val p = (cls.getMethod("getPassengerWarning").invoke(obj) as? Number)?.toInt() ?: 0
            (d != 0) to (p != 0)
        }.getOrNull()
    }

    private fun publish() {
        val body = effectiveBody()
        val tires = LauncherTireRepository.state.value
        val list = buildList {
            fun add(id: LauncherAlertId, on: Boolean, severity: LauncherAlertSeverity) {
                if (on) add(LauncherVehicleAlert(id, severity))
            }
            if (LauncherDevVehicleState.showAllIndicators) {
                LauncherAlertId.entries.forEach { id ->
                    val severity = when (id) {
                        LauncherAlertId.SeatBeltDriver,
                        LauncherAlertId.SeatBeltPassenger,
                        LauncherAlertId.TirePressure,
                        LauncherAlertId.HighTemperature,
                        LauncherAlertId.SysFault,
                        LauncherAlertId.BattFault,
                        LauncherAlertId.ChargeFault,
                        LauncherAlertId.HvFaultStop -> LauncherAlertSeverity.Critical
                        else -> LauncherAlertSeverity.Warning
                    }
                    add(LauncherVehicleAlert(id, severity))
                }
            } else {
                add(LauncherAlertId.SeatBeltDriver, driverBelt || LauncherDevVehicleState.seatBeltDriver, LauncherAlertSeverity.Critical)
                add(LauncherAlertId.SeatBeltPassenger, passengerBelt || LauncherDevVehicleState.seatBeltPassenger, LauncherAlertSeverity.Critical)
                add(LauncherAlertId.SeatBeltRearLeft, rearLeftBelt, LauncherAlertSeverity.Warning)
                add(LauncherAlertId.SeatBeltRearMid, rearMidBelt, LauncherAlertSeverity.Warning)
                add(LauncherAlertId.SeatBeltRearRight, rearRightBelt, LauncherAlertSeverity.Warning)
                add(LauncherAlertId.DoorDriver, body.doorFlOpen, LauncherAlertSeverity.Warning)
                add(LauncherAlertId.DoorPassenger, body.doorFrOpen, LauncherAlertSeverity.Warning)
                add(LauncherAlertId.DoorRearLeft, body.doorRlOpen, LauncherAlertSeverity.Warning)
                add(LauncherAlertId.DoorRearRight, body.doorRrOpen, LauncherAlertSeverity.Warning)
                add(LauncherAlertId.HoodOpen, body.hoodOpen, LauncherAlertSeverity.Warning)
                add(LauncherAlertId.TrunkOpen, body.tailgateOpen, LauncherAlertSeverity.Warning)
                add(LauncherAlertId.TirePressure, tires.hasAttention, LauncherAlertSeverity.Critical)
                add(LauncherAlertId.LowFuel, lowFuel, LauncherAlertSeverity.Warning)
                add(LauncherAlertId.Speeding, speeding, LauncherAlertSeverity.Warning)
                add(LauncherAlertId.HighTemperature, highTemp, LauncherAlertSeverity.Critical)
                add(LauncherAlertId.PressBrake, pressBrake, LauncherAlertSeverity.Warning)
                add(LauncherAlertId.SysFault, sysFault, LauncherAlertSeverity.Critical)
                add(LauncherAlertId.BattFault, battFault || packThermal, LauncherAlertSeverity.Critical)
                add(LauncherAlertId.ChargeFault, chargeFault, LauncherAlertSeverity.Critical)
                add(LauncherAlertId.HvFaultStop, hvFaultStop, LauncherAlertSeverity.Critical)
                add(LauncherAlertId.PowerModeFail, powerModeFail, LauncherAlertSeverity.Warning)
                add(LauncherAlertId.LowBatterySoc, lowSoc, LauncherAlertSeverity.Warning)
            }
        }
        _state.value = LauncherVehicleAlertsState(list)
    }

    private fun effectiveBody(): VehicleBodyState {
        return if (LauncherDevVehicleState.simulateEnabled) {
            LauncherDevVehicleState.bodyState()
        } else {
            LauncherVehicleBodyRepository.state.value
        }
    }

    private fun clearSignals() {
        driverBelt = false
        passengerBelt = false
        rearLeftBelt = false
        rearMidBelt = false
        rearRightBelt = false
        pressBrake = false
        hvFaultStop = false
        chargeFault = false
        sysFault = false
        battFault = false
        lowSoc = false
        packThermal = false
        speeding = false
        highTemp = false
        lowFuel = false
        powerModeFail = false
    }

    private fun engineInstance(): Any? = runCatching {
        Class.forName(ENGINE_CLASS).getMethod("getInstance").invoke(null)
    }.getOrNull()

    private fun byteActive(obj: Any, getter: String): Boolean =
        runCatching {
            ((obj.javaClass.getMethod(getter).invoke(obj) as? Number)?.toInt() ?: 0) != 0
        }.getOrDefault(false)
}
