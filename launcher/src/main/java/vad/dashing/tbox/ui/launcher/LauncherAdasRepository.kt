package vad.dashing.tbox.ui.launcher

import android.os.Handler
import android.os.Looper
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
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.TsrStaleWatchdog
import vad.dashing.tbox.mbcan.UniversalCanRepository
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/**
 * Live ADAS state for the launcher (FRM target object + LKA lanes + camera TSR).
 * Registers mbCAN / MBAdas push listeners while the launcher is visible.
 */
object LauncherAdasRepository {
    private const val ENGINE_CLASS = "com.mengbo.mbCan.MBCanEngine"
    private const val FRM_INFO_CLASS = "com.mengbo.mbCan.entity.MBCanVehicleFrmDectInfo"
    private const val LKA_STATUS_CLASS = "com.mengbo.mbCan.entity.MBCanVehicleLkaSlaStatus"

    private val stateLock = Any()

    @Volatile private var active = false
    private var frmInfoListenerProxy: Any? = null
    private var lkaStatusListenerProxy: Any? = null
    private var bsdListenerProxy: Any? = null
    private var rctaListenerProxy: Any? = null
    private var dowListenerProxy: Any? = null
    private var radarListenerProxy: Any? = null
    // Startup garbage filter: vendor stack emits transient radar frames right after
    // registration — ignore frames during the initial 500ms grace window.
    private var radarGraceUntilMs = 0L
    private var lastRadarMs = 0L
    private var pdcActive = false
    @Volatile private var lastSpeedKmh = 0f
    private val adasScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var pdcWatchdogJob: Job? = null
    @Volatile private var lasModeJob: Job? = null
    @Volatile private var cachedLasMode: Int = -1
    private const val PDC_STALE_MS = 1_200L
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
        synchronized(stateLock) {
            tsrSign = LauncherAdasTsrSign()
        }
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
        startLasModeWatchdog()
        active = frmInfoListenerProxy != null ||
            lkaStatusListenerProxy != null ||
            bsdListenerProxy != null ||
            rctaListenerProxy != null ||
            dowListenerProxy != null ||
            radarListenerProxy != null
    }

    private fun startLasModeWatchdog() {
        if (lasModeJob != null) return
        lasModeJob = adasScope.launch {
            while (isActive) {
                if (MbCanEngineFacade.isInitialized()) {
                    cachedLasMode = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION) ?: -1
                }
                delay(3_000L)
            }
        }
    }

    fun stop() {
        unregisterFrmListener()
        unregisterLkaListener()
        unregisterBsdListener()
        unregisterRctaListener()
        unregisterDowListener()
        unregisterRadarListener()
        pdcWatchdogJob?.cancel()
        pdcWatchdogJob = null
        lasModeJob?.cancel()
        lasModeJob = null
        cachedLasMode = -1
        MbAdasTsrFacade.stop()
        tsrWatchdog.cancel()
        synchronized(publishLock) {
            mainHandler.removeCallbacks(publishRunnable)
            publishScheduled = false
        }
        active = false
        clearSnapshots()
        _state.value = LauncherAdasState()
    }

    private fun registerTsrListener() {
        MbAdasTsrFacade.ensureListening { raw ->
            val decoded = decodeTsrSign(
                valueRaw = raw.valueRaw,
                unitRaw = raw.unitRaw,
                confidence = raw.confidence,
                signClass = raw.signClass,
                posX = raw.posX,
                posY = raw.posY,
            )
            synchronized(stateLock) {
                tsrSign = decoded
            }
            if (decoded.valid) {
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
                val srr = byteField(alarm, "getSRR_1_SystemState")
                val left = decodeRearThreatLevel(byteField(alarm, "getLeftSts"))
                val right = decodeRearThreatLevel(byteField(alarm, "getRightSts"))
                synchronized(stateLock) {
                    srrSystemState = srr
                    rearThreats = rearThreats.copy(
                        bsdLeft = left,
                        bsdRight = right,
                    )
                }
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
                val left = decodeRearThreatLevel(byteField(alarm, "getLeftSts"), hardOnly = true)
                val right = decodeRearThreatLevel(byteField(alarm, "getRightSts"), hardOnly = true)
                val rcwLevel = decodeRearThreatLevel(byteField(alarm, "getRCWWarning"))
                synchronized(stateLock) {
                    rearThreats = rearThreats.copy(
                        rctaLeft = left,
                        rctaRight = right,
                        rcw = rcwLevel,
                    )
                }
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
                val left = decodeRearThreatLevel(byteField(alarm, "getLeftSts"))
                val right = decodeRearThreatLevel(byteField(alarm, "getRightSts"))
                synchronized(stateLock) {
                    rearThreats = rearThreats.copy(
                        dowLeft = left,
                        dowRight = right,
                    )
                }
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
                    var shouldPublish = false
                    synchronized(stateLock) {
                        lastRadarMs = now
                        if (now >= radarGraceUntilMs) {
                            pdcActive = true
                            if (zones != pdcZones) {
                                pdcZones = zones
                            }
                            shouldPublish = true
                        }
                    }
                    if (shouldPublish) {
                        publish()
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
            synchronized(stateLock) {
                radarGraceUntilMs = android.os.SystemClock.uptimeMillis() + 500L
            }
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
        synchronized(stateLock) {
            radarGraceUntilMs = 0L
        }
    }

    private fun intField(obj: Any, getter: String): Int =
        runCatching {
            (obj.javaClass.getMethod(getter).invoke(obj) as? Number)?.toInt() ?: 0
        }.getOrDefault(0)

    private fun startPdcWatchdog() {
        if (pdcWatchdogJob?.isActive == true) return
        pdcWatchdogJob = adasScope.launch {
            launch {
                UniversalCanRepository.parkingRadarState.collect { radarState ->
                    if (radarState !is MbCanBinaryState.On && !LauncherDevVehicleState.simulateEnabledFlow.value) {
                        synchronized(stateLock) {
                            pdcActive = false
                            if (pdcZones.hasAny) {
                                pdcZones = LauncherPdcZones()
                            }
                        }
                        publish()
                    }
                }
            }
            while (isActive) {
                delay(300)
                val now = android.os.SystemClock.uptimeMillis()
                val drivingAway = lastSpeedKmh > PDC_DRIVE_CLEAR_KMH
                var shouldPublish = false
                synchronized(stateLock) {
                    val stale = lastRadarMs > 0L && now - lastRadarMs > PDC_STALE_MS
                    if ((stale || drivingAway) && (pdcActive || pdcZones.hasAny)) {
                        pdcActive = false
                        pdcZones = LauncherPdcZones()
                        shouldPublish = true
                    }
                }
                if (shouldPublish) {
                    publish()
                }
            }
        }
    }

    private fun parseRadarSensor(raw: Any?): LauncherPdcZones? = runCatching {
        val sensor = raw ?: return null
        val work = intField(sensor, "getRadarWorkSts")
        // 0 = module off / no sweep. Do not keep the last obstacle after PAS shuts down.
        if (work == 0) return LauncherPdcZones()
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
        var scheduleGapClear = false
        synchronized(stateLock) {
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
                scheduleGapClear = true
            }
            if (gapValid) {
                frmTimeGapLastSeenValid = newGap
            }
            frmTimeGap = newGap
        }
        if (scheduleGapClear) {
            mainHandler.removeCallbacks(clearTimeGapFlashRunnable)
            mainHandler.postDelayed(clearTimeGapFlashRunnable, TIME_GAP_FLASH_MS + 50L)
        }
        publish()
    }

    private fun onLkaStatus(snapshot: LkaSnapshot) {
        synchronized(stateLock) {
            lkaLeft = snapshot.leftVisualization
            lkaRight = snapshot.rightVisualization
            lkaStatus = snapshot.lkaStatus
            lkaAdasTakeOver = snapshot.adasTakeOverReq
            lkaSlaSpdLimit = snapshot.slaSpdLimit
            lkaSlaSpdLimitWarning = snapshot.slaSpdLimitWarning
            lkaHma = snapshot.hmaStatus
            lkaTja = snapshot.tjaMode
        }
        publish()
    }

    private const val ADAS_PUBLISH_THROTTLE_MS = 33L // Max 30 Hz updates to Compose UI
    private val mainHandler = Handler(Looper.getMainLooper())
    private val publishLock = Any()
    @Volatile private var publishScheduled = false

    private val clearTimeGapFlashRunnable = Runnable {
        synchronized(stateLock) {
            timeGapFlashUntilMs = 0L
        }
        publish()
    }

    private val publishRunnable = Runnable {
        synchronized(publishLock) {
            publishScheduled = false
        }
        doPublish()
    }

    private fun publish(immediate: Boolean = false) {
        if (immediate) {
            synchronized(publishLock) {
                mainHandler.removeCallbacks(publishRunnable)
                publishScheduled = false
            }
            doPublish()
            return
        }
        synchronized(publishLock) {
            if (publishScheduled) return
            publishScheduled = true
            mainHandler.postDelayed(publishRunnable, ADAS_PUBLISH_THROTTLE_MS)
        }
    }

    private fun doPublish() {
        val lasMode = cachedLasMode
        val newState = synchronized(stateLock) {
            buildLauncherAdasState(
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
                lasModeRaw = lasMode,
                adasTakeOverRaw = lkaAdasTakeOver,
                slaSpdLimitRaw = lkaSlaSpdLimit,
                slaSpdLimitWarningRaw = lkaSlaSpdLimitWarning,
                timeGapFlashUntilMs = timeGapFlashUntilMs,
                tsr = tsrSign,
                rearThreats = rearThreats,
                pdc = pdcZones,
                pdcActive = pdcActive && (android.os.SystemClock.uptimeMillis() - lastRadarMs < PDC_STALE_MS),
                hmaRaw = lkaHma,
                tjaRaw = lkaTja,
                srrSystemRaw = srrSystemState,
            )
        }
        if (newState != _state.value) {
            _state.value = newState
        }
    }

    fun triggerTimeGapFlash(ms: Long = 3000L) {
        synchronized(stateLock) {
            timeGapFlashUntilMs = android.os.SystemClock.uptimeMillis() + ms
        }
        mainHandler.removeCallbacks(clearTimeGapFlashRunnable)
        mainHandler.postDelayed(clearTimeGapFlashRunnable, ms + 50L)
        publish()
    }

    fun clearTimeGapFlash() {
        mainHandler.removeCallbacks(clearTimeGapFlashRunnable)
        synchronized(stateLock) {
            timeGapFlashUntilMs = 0L
        }
        publish()
    }

    private fun clearSnapshots() {
        mainHandler.removeCallbacks(clearTimeGapFlashRunnable)
        synchronized(stateLock) {
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
            pdcActive = false
            lastRadarMs = 0L
        }
    }
}
