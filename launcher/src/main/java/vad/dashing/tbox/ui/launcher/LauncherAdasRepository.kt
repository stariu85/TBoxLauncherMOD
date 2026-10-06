package vad.dashing.tbox.ui.launcher

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
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
import vad.dashing.tbox.mbcan.MbAdasTsrFacade
import vad.dashing.tbox.mbcan.MbCanAvailability
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.mbcan.TsrStaleWatchdog

/**
 * Live ADAS state for the launcher (FRM target object + LKA lanes + camera TSR).
 * Registers mbCAN / MBAdas push listeners while the launcher is visible.
 */
object LauncherAdasRepository {
    private const val ENGINE_CLASS = "com.mengbo.mbCan.MBCanEngine"
    private const val FRM_INFO_CLASS = "com.mengbo.mbCan.entity.MBCanVehicleFrmDectInfo"
    private const val LKA_STATUS_CLASS = "com.mengbo.mbCan.entity.MBCanVehicleLkaSlaStatus"

    private var active = false
    private var frmInfoListenerProxy: Any? = null
    private var lkaStatusListenerProxy: Any? = null
    private var bsdListenerProxy: Any? = null
    private var rctaListenerProxy: Any? = null
    private var dowListenerProxy: Any? = null
    private var radarListenerProxy: Any? = null
    // Startup garbage filter: vendor stack emits transient radar frames right after
    // registration — ignore the first frames and require two consecutive identical
    // frames before a reading becomes visible.
    private var radarGraceUntilMs = 0L
    private var radarPending: LauncherPdcZones? = null
    private var lastRadarMs = 0L
    @Volatile private var lastSpeedKmh = 0f
    private val adasScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pdcWatchdogJob: Job? = null
    private const val PDC_STALE_MS = 1_400L
    private const val PDC_DRIVE_CLEAR_KMH = 18f

    private val _state = MutableStateFlow(LauncherAdasState())
    val state: StateFlow<LauncherAdasState> = _state.asStateFlow()

    private var frmAccMode: Byte = 0
    private var frmVSetDis: Byte = 0
    private var frmDxTarObj: Byte = 0
    private var frmObjValid: Byte = 0
    private var frmFrontObjectType: Byte = 0
    private var frmTextInfo: Byte = 0
    private var frmTakeOverReq: Byte = 0
    private var frmObjectDx: Byte = 0
    private var frmFcwPreWarning: Byte = 0
    private var frmDistanceWarning: Byte = 0
    private var frmTimeGap: Byte = 0
    private var frmTimeGapLastSeenValid: Byte = 0
    /** [android.os.SystemClock.uptimeMillis] deadline to flash the ACC following-distance chip. */
    private var timeGapFlashUntilMs: Long = 0L

    private const val TIME_GAP_FLASH_MS = 5_000L
    private var lkaLeft: Byte = 0
    private var lkaRight: Byte = 0
    private var lkaStatus: Byte = 0
    private var lkaAdasTakeOver: Byte = 0
    private var lkaSlaSpdLimit: Byte = 0
    private var lkaSlaSpdLimitWarning: Byte = 0
    private var lkaHma: Byte = 0
    private var lkaTja: Byte = 0
    private var srrSystemState: Byte = 0
    private var tsrSign: LauncherAdasTsrSign = LauncherAdasTsrSign()
    private var rearThreats: LauncherRearThreats = LauncherRearThreats()
    private var pdcZones: LauncherPdcZones = LauncherPdcZones()

    private val tsrWatchdog = TsrStaleWatchdog {
        tsrSign = LauncherAdasTsrSign()
        publish()
    }

    fun updateMotion(speedKmh: Float) {
        lastSpeedKmh = speedKmh
    }

    fun ensureActive() {
        if (MbCanEngineFacade.ensureInitialized() !is MbCanAvailability.Available) return
        // Registration is intentionally retried for every missing listener. On the HU
        // individual ADAS endpoints can become available later than the mbCAN engine
        // (especially BSD/DOW while ignition is off).
        registerFrmListener()
        registerLkaListener()
        registerTsrListener()
        registerBsdListener()
        registerRctaListener()
        registerDowListener()
        registerRadarListener()
        startPdcWatchdog()
        active = frmInfoListenerProxy != null ||
            lkaStatusListenerProxy != null ||
            bsdListenerProxy != null ||
            rctaListenerProxy != null ||
            dowListenerProxy != null ||
            radarListenerProxy != null
    }

    fun stop() {
        if (!active) return
        unregisterFrmListener()
        unregisterLkaListener()
        unregisterBsdListener()
        unregisterRctaListener()
        unregisterDowListener()
        unregisterRadarListener()
        pdcWatchdogJob?.cancel()
        pdcWatchdogJob = null
        MbAdasTsrFacade.stop()
        tsrWatchdog.cancel()
        active = false
        _state.value = LauncherAdasState()
        clearSnapshots()
    }

    private fun registerTsrListener() {
        MbAdasTsrFacade.ensureListening { raw ->
            tsrSign = decodeTsrSign(
                valueRaw = raw.valueRaw,
                unitRaw = raw.unitRaw,
                confidence = raw.confidence,
                signClass = raw.signClass,
                posX = raw.posX,
                posY = raw.posY,
            )
            if (tsrSign.valid) {
                tsrWatchdog.kick()
            } else {
                tsrWatchdog.cancel()
            }
            publish()
        }
    }

    private fun registerFrmListener() {
        if (frmInfoListenerProxy != null) return
        val inst = engineInstance() ?: return
        val iface = runCatching {
            Class.forName("com.mengbo.mbCan.interfaces.IMBCanVehicleFrmDectInfoCallback")
        }.getOrNull() ?: return
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _, method, args ->
            if (method.name == "onCanVehicleFrmInfo" && args?.isNotEmpty() == true) {
                parseFrmInfo(args[0])?.let { onFrmInfo(it) }
            }
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
        runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            engineClass.getMethod(
                "registIMBVehicleFrmDectInfoListener",
                iface,
            ).invoke(inst, proxy)
            frmInfoListenerProxy = proxy
        }
    }

    private fun unregisterFrmListener() {
        val inst = engineInstance()
        if (inst != null && frmInfoListenerProxy != null) {
            runCatching {
                Class.forName(ENGINE_CLASS)
                    .getMethod("unRegistIMBVehicleFrmDectInfoListener")
                    .invoke(inst)
            }
        }
        frmInfoListenerProxy = null
    }

    private fun registerLkaListener() {
        if (lkaStatusListenerProxy != null) return
        val inst = engineInstance() ?: return
        val iface = runCatching {
            Class.forName("com.mengbo.mbCan.interfaces.IMBCanVehicleLkaSlaStatusCallback")
        }.getOrNull() ?: return
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _, method, args ->
            if (method.name == "onVehicleLkaSlaStatus" && args?.isNotEmpty() == true) {
                parseLkaStatus(args[0])?.let { onLkaStatus(it) }
            }
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
        runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            engineClass.getMethod(
                "registIMBCanVehicleLkaSlaStatusListener",
                iface,
            ).invoke(inst, proxy)
            lkaStatusListenerProxy = proxy
        }
    }

    private fun unregisterLkaListener() {
        val inst = engineInstance()
        if (inst != null && lkaStatusListenerProxy != null) {
            runCatching {
                Class.forName(ENGINE_CLASS)
                    .getMethod("unRegistIMBCanVehicleLkaSlaStatusListener")
                    .invoke(inst)
            }
        }
        lkaStatusListenerProxy = null
    }

    private fun registerBsdListener() {
        if (bsdListenerProxy != null) return
        val inst = engineInstance() ?: return
        val iface = runCatching {
            Class.forName("com.mengbo.mbCan.interfaces.IMbCanBsdAlarmCallback")
        }.getOrNull() ?: return
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _, method, args ->
            if (method.name == "onBsdAlarm" && args?.isNotEmpty() == true) {
                val alarm = args[0] ?: return@InvocationHandler null
                srrSystemState = byteField(alarm, "getSRR_1_SystemState")
                rearThreats = rearThreats.copy(
                    bsdLeft = decodeRearThreatLevel(byteField(alarm, "getLeftSts")),
                    bsdRight = decodeRearThreatLevel(byteField(alarm, "getRightSts")),
                )
                publish()
            }
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
        runCatching {
            Class.forName(ENGINE_CLASS)
                .getMethod("registIMBBsdAlarmListener", iface)
                .invoke(inst, proxy)
            bsdListenerProxy = proxy
        }
    }

    private fun unregisterBsdListener() {
        val inst = engineInstance()
        if (inst != null && bsdListenerProxy != null) {
            runCatching {
                Class.forName(ENGINE_CLASS).getMethod("unRegistIMBBsdAlarmListener").invoke(inst)
            }
        }
        bsdListenerProxy = null
    }

    private fun registerRctaListener() {
        if (rctaListenerProxy != null) return
        val inst = engineInstance() ?: return
        val iface = runCatching {
            Class.forName("com.mengbo.mbCan.interfaces.IMbCanRCTAAlarmCallback")
        }.getOrNull() ?: return
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _, method, args ->
            if (method.name == "onRctaAlarmChange" && args?.isNotEmpty() == true) {
                val alarm = args[0] ?: return@InvocationHandler null
                // OEM adascard shows RCTA car only at hard (==2); RCW uses 1/2.
                rearThreats = rearThreats.copy(
                    rctaLeft = decodeRearThreatLevel(byteField(alarm, "getLeftSts"), hardOnly = true),
                    rctaRight = decodeRearThreatLevel(byteField(alarm, "getRightSts"), hardOnly = true),
                    rcw = decodeRearThreatLevel(byteField(alarm, "getRCWWarning")),
                )
                publish()
            }
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
        runCatching {
            Class.forName(ENGINE_CLASS)
                .getMethod("registIMBRCTAAlarmListener", iface)
                .invoke(inst, proxy)
            rctaListenerProxy = proxy
        }
    }

    private fun unregisterRctaListener() {
        val inst = engineInstance()
        if (inst != null && rctaListenerProxy != null) {
            runCatching {
                Class.forName(ENGINE_CLASS).getMethod("unRegistIMBRCTAAlarmListener").invoke(inst)
            }
        }
        rctaListenerProxy = null
    }

    private fun registerDowListener() {
        if (dowListenerProxy != null) return
        val inst = engineInstance() ?: return
        val iface = runCatching {
            Class.forName("com.mengbo.mbCan.interfaces.IMbCanDowAlarmCallback")
        }.getOrNull() ?: return
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _, method, args ->
            if (method.name == "onDowAlarm" && args?.isNotEmpty() == true) {
                val alarm = args[0] ?: return@InvocationHandler null
                rearThreats = rearThreats.copy(
                    dowLeft = decodeRearThreatLevel(byteField(alarm, "getLeftSts")),
                    dowRight = decodeRearThreatLevel(byteField(alarm, "getRightSts")),
                )
                publish()
            }
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
        runCatching {
            Class.forName(ENGINE_CLASS)
                .getMethod("registIMBDowAlarmListener", iface)
                .invoke(inst, proxy)
            dowListenerProxy = proxy
        }
    }

    private fun unregisterDowListener() {
        val inst = engineInstance()
        if (inst != null && dowListenerProxy != null) {
            runCatching {
                Class.forName(ENGINE_CLASS).getMethod("unRegistIMBDowAlarmListener").invoke(inst)
            }
        }
        dowListenerProxy = null
    }

    private fun registerRadarListener() {
        if (radarListenerProxy != null) return
        val inst = engineInstance() ?: return
        val iface = runCatching {
            Class.forName("com.mengbo.mbCan.interfaces.IMbCanRadarSensorCallback")
        }.getOrNull() ?: return
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _, method, args ->
            if (method.name == "onRadarSensorChange" && args?.isNotEmpty() == true) {
                parseRadarSensor(args[0])?.let { zones ->
                    val now = android.os.SystemClock.uptimeMillis()
                    lastRadarMs = now
                    if (now >= radarGraceUntilMs) {
                        if (zones == radarPending || zones == pdcZones) {
                            pdcZones = zones
                            publish()
                        }
                        radarPending = zones
                    }
                }
            }
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
        runCatching {
            Class.forName(ENGINE_CLASS)
                .getMethod("registRadarSensorListener", iface)
                .invoke(inst, proxy)
            radarListenerProxy = proxy
            radarGraceUntilMs = android.os.SystemClock.uptimeMillis() + 2500L
            radarPending = null
        }
    }

    private fun unregisterRadarListener() {
        val inst = engineInstance()
        if (inst != null && radarListenerProxy != null) {
            runCatching {
                Class.forName(ENGINE_CLASS).getMethod("unregistRadarSensorListener").invoke(inst)
            }
        }
        radarListenerProxy = null
        radarGraceUntilMs = 0L
        radarPending = null
    }

    private fun intField(obj: Any, getter: String): Int =
        runCatching {
            (obj.javaClass.getMethod(getter).invoke(obj) as? Number)?.toInt() ?: 0
        }.getOrDefault(0)

    private fun startPdcWatchdog() {
        if (pdcWatchdogJob?.isActive == true) return
        pdcWatchdogJob = adasScope.launch {
            while (isActive) {
                delay(400)
                val now = android.os.SystemClock.uptimeMillis()
                val stale = lastRadarMs > 0L && now - lastRadarMs > PDC_STALE_MS
                val drivingAway = lastSpeedKmh > PDC_DRIVE_CLEAR_KMH
                if (pdcZones.hasAny && (stale || drivingAway)) {
                    pdcZones = LauncherPdcZones()
                    radarPending = null
                    publish()
                }
            }
        }
    }

    private fun parseRadarSensor(raw: Any?): LauncherPdcZones? = runCatching {
        val sensor = raw ?: return null
        val work = intField(sensor, "getRadarWorkSts")
        val detect = intField(sensor, "getRadarDetectSts")
        // 0 = module off / no sweep. Do not keep the last obstacle after PAS shuts down.
        if (work == 0 || detect == 0) return LauncherPdcZones()
        // OEM L/R on this car is mirrored vs the 3D body (driver-left is packet-right).
        LauncherPdcZones(
            frontSideLeftCm = intField(sensor, "getRHSF_Distance"),
            frontLeftCm = intField(sensor, "getRHF_Distance"),
            frontMidLeftCm = intField(sensor, "getRHMF_Distance"),
            frontMidRightCm = intField(sensor, "getLHMF_Distance"),
            frontRightCm = intField(sensor, "getLHF_Distance"),
            frontSideRightCm = intField(sensor, "getLHSF_Distance"),
            rearSideLeftCm = intField(sensor, "getRHSR_Distance"),
            rearLeftCm = intField(sensor, "getRHR_Distance"),
            rearMidLeftCm = intField(sensor, "getRHMR_Distance"),
            rearMidRightCm = intField(sensor, "getLHMR_Distance"),
            rearRightCm = intField(sensor, "getLHR_Distance"),
            rearSideRightCm = intField(sensor, "getLHSR_Distance"),
        )
    }.getOrNull()

    private fun byteField(obj: Any, getter: String): Byte =
        runCatching {
            (obj.javaClass.getMethod(getter).invoke(obj) as? Number)?.toByte() ?: 0
        }.getOrDefault(0)

    private fun engineInstance(): Any? = runCatching {
        Class.forName(ENGINE_CLASS).getMethod("getInstance").invoke(null)
    }.getOrNull()

    private data class FrmSnapshot(
        val accMode: Byte,
        val vSetDis: Byte,
        val dxTarObj: Byte,
        val objValid: Byte,
        val frontObjectType: Byte,
        val textInfo: Byte,
        val takeOverReq: Byte,
        val objectDx: Byte,
        val fcwPreWarning: Byte,
        val distanceWarning: Byte,
        val timeGap: Byte,
    )

    private data class LkaSnapshot(
        val leftVisualization: Byte,
        val rightVisualization: Byte,
        val lkaStatus: Byte,
        val adasTakeOverReq: Byte,
        val slaSpdLimit: Byte,
        val slaSpdLimitWarning: Byte,
        val hmaStatus: Byte,
        val tjaMode: Byte,
    )

    private fun parseFrmInfo(raw: Any?): FrmSnapshot? = runCatching {
        val cls = Class.forName(FRM_INFO_CLASS)
        FrmSnapshot(
            accMode = cls.getMethod("getFRM_3_ACCMode").invoke(raw) as Byte,
            vSetDis = cls.getMethod("getFRM_3_VSetDis").invoke(raw) as Byte,
            dxTarObj = cls.getMethod("getFRM_3_DxTarObj").invoke(raw) as Byte,
            objValid = cls.getMethod("getFRM_3_ObjValid").invoke(raw) as Byte,
            frontObjectType = cls.getMethod("getFRM_3_FrontObject_Type").invoke(raw) as Byte,
            textInfo = cls.getMethod("getFRM_3_Textinfo").invoke(raw) as Byte,
            takeOverReq = cls.getMethod("getFRM_3_TakeOverReq").invoke(raw) as Byte,
            objectDx = cls.getMethod("getFRM_3_Obiect_Dx").invoke(raw) as Byte,
            fcwPreWarning = cls.getMethod("getFRM_3_FCW_PreWarning").invoke(raw) as Byte,
            distanceWarning = cls.getMethod("getFRM_3_DistanceWarning").invoke(raw) as Byte,
            timeGap = cls.getMethod("getFRM_3_TimeGapSet_ICM").invoke(raw) as Byte,
        )
    }.getOrNull()

    private fun parseLkaStatus(raw: Any?): LkaSnapshot? = runCatching {
        val cls = Class.forName(LKA_STATUS_CLASS)
        LkaSnapshot(
            leftVisualization = cls.getMethod("getFCM_2_LDW_LKA_LeftVisualization").invoke(raw) as Byte,
            rightVisualization = cls.getMethod("getFCM_2_LDW_LKA_RightVisualization").invoke(raw) as Byte,
            lkaStatus = cls.getMethod("getFCM_2_LDW_LKA_Status").invoke(raw) as Byte,
            adasTakeOverReq = cls.getMethod("getFCM_2_ADAS_TakeoverReq").invoke(raw) as Byte,
            slaSpdLimit = cls.getMethod("getFCM_2_SLASpdlimit").invoke(raw) as Byte,
            slaSpdLimitWarning = cls.getMethod("getFCM_2_SLASpdlimitWarning").invoke(raw) as Byte,
            hmaStatus = cls.getMethod("getFCM_2_HMA_Status").invoke(raw) as Byte,
            tjaMode = cls.getMethod("getFCM_2_TJA_ICA_Mode").invoke(raw) as Byte,
        )
    }.getOrNull()

    private fun onFrmInfo(snapshot: FrmSnapshot) {
        frmAccMode = snapshot.accMode
        frmVSetDis = snapshot.vSetDis
        frmDxTarObj = snapshot.dxTarObj
        frmObjValid = snapshot.objValid
        frmFrontObjectType = snapshot.frontObjectType
        frmTextInfo = snapshot.textInfo
        frmTakeOverReq = snapshot.takeOverReq
        frmObjectDx = snapshot.objectDx
        frmFcwPreWarning = snapshot.fcwPreWarning
        frmDistanceWarning = snapshot.distanceWarning
        val newGap = snapshot.timeGap
        val now = android.os.SystemClock.uptimeMillis()
        val newGapCode = byteToUnsigned(newGap)
        val lastGapCode = byteToUnsigned(frmTimeGapLastSeenValid)
        val gapValid = newGapCode in 0..2
        val lastValid = lastGapCode in 0..2
        if (gapValid && lastValid && newGapCode != lastGapCode) {
            timeGapFlashUntilMs = now + TIME_GAP_FLASH_MS
        }
        if (gapValid) {
            frmTimeGapLastSeenValid = newGap
        }
        frmTimeGap = newGap
        publish()
    }

    private fun onLkaStatus(snapshot: LkaSnapshot) {
        lkaLeft = snapshot.leftVisualization
        lkaRight = snapshot.rightVisualization
        lkaStatus = snapshot.lkaStatus
        lkaAdasTakeOver = snapshot.adasTakeOverReq
        lkaSlaSpdLimit = snapshot.slaSpdLimit
        lkaSlaSpdLimitWarning = snapshot.slaSpdLimitWarning
        lkaHma = snapshot.hmaStatus
        lkaTja = snapshot.tjaMode
        publish()
    }

    private fun publish() {
        _state.value = buildLauncherAdasState(
            accModeRaw = frmAccMode,
            vSetDisRaw = frmVSetDis,
            objValidRaw = frmObjValid,
            frontObjectTypeRaw = frmFrontObjectType,
            dxTarObjRaw = frmDxTarObj,
            objectDxRaw = frmObjectDx,
            takeOverRaw = frmTakeOverReq,
            textInfoRaw = frmTextInfo,
            fcwPreWarningRaw = frmFcwPreWarning,
            distanceWarningRaw = frmDistanceWarning,
            timeGapRaw = frmTimeGap,
            leftLaneRaw = lkaLeft,
            rightLaneRaw = lkaRight,
            lkaStatusRaw = lkaStatus,
            adasTakeOverRaw = lkaAdasTakeOver,
            slaSpdLimitRaw = lkaSlaSpdLimit,
            slaSpdLimitWarningRaw = lkaSlaSpdLimitWarning,
            timeGapFlashUntilMs = timeGapFlashUntilMs,
            tsr = tsrSign,
            rearThreats = rearThreats,
            pdc = pdcZones,
            hmaRaw = lkaHma,
            tjaRaw = lkaTja,
            srrSystemRaw = srrSystemState,
        )
    }

    fun triggerTimeGapFlash(ms: Long = 3000L) {
        timeGapFlashUntilMs = android.os.SystemClock.uptimeMillis() + ms
        publish()
    }

    private fun clearSnapshots() {
        frmAccMode = 0
        frmVSetDis = 0
        frmDxTarObj = 0
        frmObjValid = 0
        frmFrontObjectType = 0
        frmTextInfo = 0
        frmTakeOverReq = 0
        frmObjectDx = 0
        frmFcwPreWarning = 0
        frmDistanceWarning = 0
        frmTimeGap = 0
        frmTimeGapLastSeenValid = 0
        timeGapFlashUntilMs = 0L
        lkaLeft = 0
        lkaRight = 0
        lkaStatus = 0
        lkaAdasTakeOver = 0
        lkaSlaSpdLimit = 0
        lkaSlaSpdLimitWarning = 0
        lkaHma = 0
        lkaTja = 0
        srrSystemState = 0
        tsrSign = LauncherAdasTsrSign()
        rearThreats = LauncherRearThreats()
        pdcZones = LauncherPdcZones()
        lastRadarMs = 0L
        radarPending = null
    }
}
