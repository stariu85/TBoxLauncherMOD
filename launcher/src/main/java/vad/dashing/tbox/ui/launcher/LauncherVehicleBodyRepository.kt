package vad.dashing.tbox.ui.launcher

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.mbcan.Android10VhalRepository
import vad.dashing.tbox.mbcan.LauncherVehicleDoorSnapshot
import vad.dashing.tbox.mbcan.MbCanAvailability
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.mbcan.VehicleBodyState
import vad.dashing.tbox.mbcan.decodeMbCanDoorByte
import vad.dashing.tbox.mbcan.decodeMbCanTrunkByte

/**
 * Polls / listens for vehicle body state (doors, tailgate, hood) from mbCAN / VHAL.
 */
object LauncherVehicleBodyRepository {
    private const val ENGINE_CLASS = "com.mengbo.mbCan.MBCanEngine"
    private const val POLL_MS = 400L
    private val scope = CoroutineScope(SupervisorJob() + LauncherCpuAffinity.canBusDispatcher)
    private var pollJob: Job? = null
    private var subscribed = false
    private var doorListenerProxy: Any? = null
    private var lastRawDoorLog: String? = null

    private val _state = MutableStateFlow(VehicleBodyState())
    val state: StateFlow<VehicleBodyState> = _state.asStateFlow()

    fun ensurePolling() {
        if (pollJob?.isActive == true) return
        subscribeDoorTelemetry()
        pollJob = scope.launch {
            while (isActive) {
                refresh()
                delay(POLL_MS)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        // MBCanEngine listener slots are global. Do not call unregistCarDorListener()
        // here: OEM CarSettings and other consumers may share the BCM subscription.
    }

    fun refresh() {
        val body = when (UniversalCanRepository.mode.value) {
            HeadUnitCanMode.Android9MbCan -> readFromMbCan()
            HeadUnitCanMode.Android10Vhal -> Android10VhalRepository.readVehicleBodyState(_state.value)
                ?: readFromMbCan()
        }
        if (body != null) {
            publish(body)
        }
    }

    private fun publish(body: VehicleBodyState) {
        val previous = _state.value
        val changed = previous != body
        _state.value = body
        if (changed) {
            vad.dashing.tbox.DiagFileLog.i(
                "Body",
                "doors fl=${body.doorFlOpen} fr=${body.doorFrOpen} rl=${body.doorRlOpen} rr=${body.doorRrOpen} " +
                    "trunk=${body.tailgateOpen} hood=${body.hoodOpen}",
            )
            LauncherVehicleAlertsRepository.refresh()
        }
    }

    private fun subscribeDoorTelemetry() {
        if (subscribed) return
        subscribed = true
        MbCanEngineFacade.subscribe(
            setOf(
                "eMBCAN_VEHICLE_BCM_STATUS",
                "eMBCAN_VEHICLE_DOOR",
            ),
        )
        registerDoorListener()
    }

    private fun registerDoorListener() {
        if (doorListenerProxy != null) return
        if (MbCanEngineFacade.ensureInitialized() !is MbCanAvailability.Available) return
        val inst = engineInstance() ?: return
        val iface = runCatching {
            Class.forName("com.mengbo.mbCan.interfaces.IMbCanVehicleDoorCallback")
        }.getOrNull() ?: return
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _, method, args ->
            if (method.name == "onVehicleDoorChange" && args?.isNotEmpty() == true) {
                val door = args[0] ?: return@InvocationHandler null
                parseDoorObject(door)?.let { publish(it.toBodyState()) }
            }
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
        runCatching {
            Class.forName(ENGINE_CLASS)
                .getMethod("registCarDorListener", iface)
                .invoke(inst, proxy)
            doorListenerProxy = proxy
        }
    }

    private fun readFromMbCan(): VehicleBodyState? {
        // Prefer dedicated door cache (type 5): BCM nested door (type 21) can keep a stale
        // trunk=open after the latch closes, which left the 3D lid stuck open.
        return MbCanEngineFacade.readVehicleDoor()?.toBodyState() ?: readDoorFromBcm()
    }

    private fun readDoorFromBcm(): VehicleBodyState? {
        if (MbCanEngineFacade.ensureInitialized() !is MbCanAvailability.Available) return null
        return runCatching {
            val bcmCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleBcmStatus")
            val bcm = MbCanEngineFacade.getMbCanData(21, bcmCls) ?: return null
            val door = bcmCls.getMethod("getDoorStatus").invoke(bcm) ?: return null
            parseDoorObject(door)?.toBodyState()
        }.getOrNull()
    }

    private fun parseDoorObject(door: Any): LauncherVehicleDoorSnapshot? = runCatching {
        val cls = door.javaClass
        LauncherVehicleDoorSnapshot(
            driverDoor = cls.getMethod("getDriverDoorSts").invoke(door) as Byte,
            passengerDoor = cls.getMethod("getPsngrDoorSts").invoke(door) as Byte,
            rearLeftDoor = cls.getMethod("getLHRdoorSts").invoke(door) as Byte,
            rearRightDoor = cls.getMethod("getRHRDoorSts").invoke(door) as Byte,
            trunk = cls.getMethod("getTrunkSts").invoke(door) as Byte,
            hood = cls.getMethod("getHoodSts").invoke(door) as Byte,
        )
    }.getOrNull()

    private fun LauncherVehicleDoorSnapshot.toBodyState(): VehicleBodyState {
        val raw =
            "fl=${driverDoor.toInt() and 0xFF} fr=${passengerDoor.toInt() and 0xFF} " +
                "rl=${rearLeftDoor.toInt() and 0xFF} rr=${rearRightDoor.toInt() and 0xFF} " +
                "trunk=${trunk.toInt() and 0xFF} hood=${hood.toInt() and 0xFF}"
        if (raw != lastRawDoorLog) {
            lastRawDoorLog = raw
            vad.dashing.tbox.DiagFileLog.i("BodyRaw", "doorBytes $raw")
        }
        val previous = _state.value
        return VehicleBodyState(
            doorFlOpen = decodeMbCanDoorByte(driverDoor, previous.doorFlOpen),
            doorFrOpen = decodeMbCanDoorByte(passengerDoor, previous.doorFrOpen),
            doorRlOpen = decodeMbCanDoorByte(rearLeftDoor, previous.doorRlOpen),
            doorRrOpen = decodeMbCanDoorByte(rearRightDoor, previous.doorRrOpen),
            tailgateOpen = decodeMbCanTrunkByte(trunk, previous.tailgateOpen),
            hoodOpen = decodeMbCanTrunkByte(hood, previous.hoodOpen),
        )
    }

    private fun engineInstance(): Any? = runCatching {
        Class.forName(ENGINE_CLASS).getMethod("getInstance").invoke(null)
    }.getOrNull()
}
