package vad.dashing.tbox.mbcan

import android.util.Log
import vad.dashing.tbox.mbcan.MbCanEngineFacade.canGetVehicleParam
import vad.dashing.tbox.mbcan.MbCanEngineFacade.ensureInitialized
import vad.dashing.tbox.mbcan.MbCanEngineFacade.syncVehicleCfgCmdListener
import vad.dashing.tbox.CanDataRepository
import vad.dashing.tbox.DclLogRecorder
import vad.dashing.tbox.ui.launcher.LauncherTireRepository
import vad.dashing.tbox.ui.launcher.LauncherVehicleAlertsRepository
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference

sealed class MbCanAvailability {
    data object Unknown : MbCanAvailability()
    data object Available : MbCanAvailability()
    data class Unavailable(val reason: String) : MbCanAvailability()
}

/**
 * Reflection-only bridge to vendor mbCAN classes.
 * Keeps app build/runtime safe when vendor library is absent.
 */
object MbCanEngineFacade {
    private const val ENGINE_CLASS = "com.mengbo.mbCan.MBCanEngine"
    private const val DATA_TYPE_CLASS = "com.mengbo.mbCan.defines.MBCanDataType"
    private const val LOG_TAG = "TBoxCan"

    private val availabilityRef = AtomicReference<MbCanAvailability>(MbCanAvailability.Unknown)
    private var engineInstance: Any? = null
    private var canGetVehicleParamMethod: Method? = null
    private var canSetVehicleParamMethod: Method? = null
    private var canSetAirConditionMethod: Method? = null
    private var canGetVehicleParamStringMethod: Method? = null
    private var canGetAudioParamMethod: Method? = null
    private var canSetAudioParamMethod: Method? = null
    private var subscribeMethod: Method? = null
    private var unSubscribeMethod: Method? = null
    private var registerCarSettingsListenerMethod: Method? = null
    private var unregisterCarSettingsListenerMethod: Method? = null
    private var registAirPurgeListenerMethod: Method? = null
    private var unregistAirPurgeListenerMethod: Method? = null
    private var settingsTelemetryProxy: Any? = null
    private var airPurgeListenerProxy: Any? = null
    private var registCmdListenerMethod: Method? = null
    private var unRegistCmdListenerMethod: Method? = null
    private var cfgVehicleDataType: Any? = null
    private var cfgAudioDataType: Any? = null
    private var vehicleCfgCmdListenerProxy: Any? = null
    private var audioCfgCmdListenerProxy: Any? = null
    private var registHardKeyListenerMethod: Method? = null
    private var unRegistHardKeyListenerMethod: Method? = null
    private var hardKeyCallback: ((keyCode: Int, keyAction: Int, extra: Byte) -> Unit)? = null
    private var hardKeyListenerProxy: Any? = null
    private var initialized = false

    private val reflectionMethodCache = java.util.concurrent.ConcurrentHashMap<Pair<Class<*>, String>, Method>()
    private val lastVehicleCfgValues = java.util.concurrent.ConcurrentHashMap<Int, Int>()
    private val lastAudioCfgValues = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    @Volatile
    private var lastSpeed: Float? = null
    @Volatile
    private var lastEngineRpm: Float? = null
    @Volatile
    private var lastEngineTemp: Float? = null
    @Volatile
    private var lastBcmLightKey: Int? = null

    private fun getCachedZeroArgMethod(clazz: Class<*>, methodName: String): Method? {
        val key = clazz to methodName
        val cached = reflectionMethodCache[key]
        if (cached != null) return cached
        val method = runCatching {
            clazz.getMethod(methodName)
        }.getOrNull() ?: clazz.methods.firstOrNull { it.name == methodName && it.parameterCount == 0 }
        if (method != null) {
            reflectionMethodCache[key] = method
        }
        return method
    }

    /**
     * Vendor native mbCAN (`MBCan_Vehicle_Get` / `getMbCanData`) is not thread-safe:
     * concurrent calls abort with `stack corruption detected` / SIGBUS.
     * Serialize every native engine entry point on this lock.
     */
    private val nativeLock = Any()

    private inline fun <T> withNativeLock(block: () -> T): T = synchronized(nativeLock, block)

    val availability: MbCanAvailability
        get() = availabilityRef.get()

    fun isInitialized(): Boolean = initialized

    @Synchronized
    fun probeAvailability(): MbCanAvailability {
        if (availabilityRef.get() is MbCanAvailability.Available && initialized) {
            return MbCanAvailability.Available
        }
        return try {
            Class.forName(ENGINE_CLASS, false, MbCanEngineFacade::class.java.classLoader)
            Log.i(LOG_TAG, "probeAvailability: engine class found")
            MbCanAvailability.Unknown
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "probeAvailability: ${t.javaClass.simpleName}: ${t.message}")
            MbCanAvailability.Unavailable("${t.javaClass.simpleName}: ${t.message ?: "unknown"}")
        }.also { availabilityRef.set(it) }
    }

    @Synchronized
    fun ensureInitialized(): MbCanAvailability {
        if (availabilityRef.get() is MbCanAvailability.Available) return MbCanAvailability.Available
        try {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getInstance = engineClass.getMethod("getInstance")
            val instance = getInstance.invoke(null) ?: run {
                val unavailable = MbCanAvailability.Unavailable("MBCanEngine.getInstance() returned null")
                availabilityRef.set(unavailable)
                return unavailable
            }
            engineInstance = instance
            canGetVehicleParamMethod = engineClass.getMethod("canGetVehicleParam", Int::class.javaPrimitiveType)
            canSetVehicleParamMethod =
                engineClass.getMethod("canSetVehicleParam", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            canSetAirConditionMethod = runCatching {
                engineClass.getMethod("canSetAirCondition", Class.forName("com.mengbo.mbCan.entity.MBAirCondition"))
            }.getOrNull()
            // Optional vendor entry point (string properties: Wi-Fi SSID/PSK/MAC, BT address...).
            // Absent on some firmware — must not fail the whole init.
            canGetVehicleParamStringMethod = runCatching {
                engineClass.getMethod("canGetVehicleParamString", Int::class.javaPrimitiveType)
            }.getOrNull()
            canGetAudioParamMethod =
                engineClass.getMethod("canGetAudioParam", Int::class.javaPrimitiveType)
            canSetAudioParamMethod =
                engineClass.getMethod("canSetAudioParam", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            subscribeMethod = engineClass.getMethod("subscribeCanDataWithList", ArrayList::class.java)
            unSubscribeMethod = engineClass.getMethod("unSubscribeCanDataWithList", ArrayList::class.java)
            registerCarSettingsListenerMethod =
                engineClass.getMethod("registIMBCarSettingsListener", Class.forName("com.mengbo.mbCan.interfaces.IMBCanSettingsCallback"))
            unregisterCarSettingsListenerMethod = engineClass.getMethod("unregistIMBCarSettingsListener")
            registAirPurgeListenerMethod = runCatching {
                engineClass.getMethod("registIMBAirPurgeListener", Class.forName("com.mengbo.mbCan.interfaces.IMBAirPurgeListener"))
            }.getOrNull()
            unregistAirPurgeListenerMethod = runCatching {
                engineClass.getMethod("unRegistIMBAirPurgeListener")
            }.getOrNull()
            registCmdListenerMethod = engineClass.getMethod(
                "registCMDListener",
                Class.forName(DATA_TYPE_CLASS),
                Class.forName("com.mengbo.mbCan.interfaces.IMBCmdListener")
            )
            unRegistCmdListenerMethod = engineClass.getMethod("unRegistCMDListener", Class.forName(DATA_TYPE_CLASS))
            registHardKeyListenerMethod = runCatching {
                engineClass.getMethod("registHardKeyListener", Class.forName("com.mengbo.mbCan.interfaces.IMBHardKeyListener"))
            }.getOrNull()
            unRegistHardKeyListenerMethod = runCatching {
                engineClass.getMethod("unRegistHardKeyListener")
            }.getOrNull()
            val dataTypeClass = Class.forName(DATA_TYPE_CLASS) as Class<out Enum<*>>
            cfgVehicleDataType = java.lang.Enum.valueOf(dataTypeClass, "eMBCAN_CFG_VEHICLE")
            cfgAudioDataType = java.lang.Enum.valueOf(dataTypeClass, "eMBCAN_CFG_AUDIO")
            initialized = true
            availabilityRef.set(MbCanAvailability.Available)
            Log.i(LOG_TAG, "ensureInitialized: OK")
            runCatching { unsubscribeUnusedChannels() }
        } catch (t: Throwable) {
            initialized = false
            availabilityRef.set(MbCanAvailability.Unavailable("${t.javaClass.simpleName}: ${t.message ?: "unknown"}"))
            Log.w(LOG_TAG, "ensureInitialized failed: ${t.javaClass.simpleName}: ${t.message}")
        }
        return availabilityRef.get()
    }

    fun canGetVehicleParam(propertyId: Int): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        vad.dashing.tbox.DclLogRecorder.recordCanRx()
        return try {
            withNativeLock {
                (canGetVehicleParamMethod?.invoke(engineInstance, propertyId) as? Int)
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** [com.mengbo.mbCan.MBCanEngine.canGetAudioParam] — [com.mengbo.mbCan.defines.MBAudioProperty] ordinal ids. */
    fun canGetAudioParam(propertyId: Int): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        vad.dashing.tbox.DclLogRecorder.recordCanRx()
        return try {
            withNativeLock {
                (canGetAudioParamMethod?.invoke(engineInstance, propertyId) as? Int)
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** [com.mengbo.mbCan.MBCanEngine.canSetAudioParam] — [com.mengbo.mbCan.defines.MBAudioProperty] value ids. */
    fun canSetAudioParam(propertyId: Int, value: Int): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        vad.dashing.tbox.DclLogRecorder.recordCanTx()
        return try {
            withNativeLock {
                (canSetAudioParamMethod?.invoke(engineInstance, propertyId, value) as? Int)
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun canSetVehicleParam(propertyId: Int, value: Int): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        vad.dashing.tbox.DclLogRecorder.recordCanTx()
        DclLogRecorder.log("CAN_TX", "canSetVehicleParam propId=$propertyId value=$value")
        return try {
            withNativeLock {
                (canSetVehicleParamMethod?.invoke(engineInstance, propertyId, value) as? Int)
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun canSetAirCondition(airCondition: com.mengbo.mbCan.entity.MBAirCondition): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        DclLogRecorder.recordCanTx()
        DclLogRecorder.log("CAN_TX", "canSetAirCondition $airCondition")
        return try {
            withNativeLock {
                (canSetAirConditionMethod?.invoke(engineInstance, airCondition) as? Int)
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** [com.mengbo.mbCan.MBCanEngine.canGetVehicleParamString] — MBVehicleProperty string ids (62/63/116...). */
    fun canGetVehicleParamString(propertyId: Int): String? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        vad.dashing.tbox.DclLogRecorder.recordCanRx()
        return try {
            withNativeLock {
                (canGetVehicleParamStringMethod?.invoke(engineInstance, propertyId) as? String)
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Thread-safe [com.mengbo.mbCan.MBCanEngine.getMbCanData].
     * Call sites outside this facade must use this — direct reflection races with
     * [canGetVehicleParam] and aborts native with stack corruption.
     */
    fun getMbCanData(dataType: Int, dataClass: Class<*>): Any? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        vad.dashing.tbox.DclLogRecorder.recordCanRx()
        val inst = engineInstance ?: return null
        return runCatching {
            withNativeLock {
                val engineClass = Class.forName(ENGINE_CLASS)
                engineClass.getMethod(
                    "getMbCanData",
                    Int::class.javaPrimitiveType,
                    Class::class.java,
                ).invoke(inst, dataType, dataClass)
            }
        }.getOrNull()
    }

    /** Returns true if the BCM indicates the brake pedal is currently depressed. */
    fun readBrakePedalPressed(): Boolean {
        return runCatching {
            val bcmClass = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleBcmStatus")
            val bcm = getMbCanData(21, bcmClass) ?: return false
            val sts = (bcmClass.getMethod("getBrakePedalSts").invoke(bcm) as? Number)?.toInt() ?: 0
            sts > 0
        }.getOrDefault(false)
    }

    /**
     * Uses the same native API as the OEM CarSettings window controls.
     * Updates target positions for any windows specified in [positions].
     * Unspecified windows retain their latest BCM positions.
     */
    fun setWindowPositions(positions: Map<Int, Int>): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            synchronized(nativeLock) {
                val engineClass = Class.forName(ENGINE_CLASS)
                val getMbCanData = engineClass.getMethod(
                    "getMbCanData",
                    Int::class.javaPrimitiveType,
                    Class::class.java,
                )
                val bcmClass = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleBcmStatus")
                val windowClass = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleWindow")
                val bcm = getMbCanData.invoke(inst, 21, bcmClass) ?: return null
                val current = bcmClass.getMethod("getVehicleWindow").invoke(bcm) ?: return null
                var frontRight = (windowClass.getMethod("getFRWindow").invoke(current) as Number).toByte()
                var frontLeft = (windowClass.getMethod("getFLWindow").invoke(current) as Number).toByte()
                var rearRight = (windowClass.getMethod("getRRWindow").invoke(current) as Number).toByte()
                var rearLeft = (windowClass.getMethod("getRLWindow").invoke(current) as Number).toByte()

                positions[0]?.let { frontLeft = it.coerceIn(0, 100).toByte() }
                positions[1]?.let { frontRight = it.coerceIn(0, 100).toByte() }
                positions[2]?.let { rearLeft = it.coerceIn(0, 100).toByte() }
                positions[3]?.let { rearRight = it.coerceIn(0, 100).toByte() }

                val command = windowClass.getConstructor(
                    Byte::class.javaPrimitiveType,
                    Byte::class.javaPrimitiveType,
                    Byte::class.javaPrimitiveType,
                    Byte::class.javaPrimitiveType,
                ).newInstance(
                    frontRight,
                    frontLeft,
                    rearRight,
                    rearLeft,
                )
                (engineClass.getMethod("canSetWindowStatus", windowClass)
                    .invoke(inst, command) as? Number)?.toInt()
            }
        }.getOrNull()
    }

    fun setWindowPosition(windowIndex: Int, positionPercent: Int): Int? =
        setWindowPositions(mapOf(windowIndex to positionPercent))

    fun setDriverWindowPosition(positionPercent: Int): Int? =
        setWindowPosition(windowIndex = 0, positionPercent = positionPercent)

    fun readWindowPosition(windowIndex: Int): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            synchronized(nativeLock) {
                val engineClass = Class.forName(ENGINE_CLASS)
                val getMbCanData = engineClass.getMethod(
                    "getMbCanData",
                    Int::class.javaPrimitiveType,
                    Class::class.java,
                )
                val bcmClass = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleBcmStatus")
                val windowClass = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleWindow")
                val bcm = getMbCanData.invoke(inst, 21, bcmClass) ?: return null
                val current = bcmClass.getMethod("getVehicleWindow").invoke(bcm) ?: return null
                val getter = when (windowIndex) {
                    0 -> "getFLWindow"
                    1 -> "getFRWindow"
                    2 -> "getRLWindow"
                    3 -> "getRRWindow"
                    else -> return null
                }
                (windowClass.getMethod(getter).invoke(current) as Number)
                    .toInt()
                    .coerceIn(0, 100)
            }
        }.getOrNull()
    }

    fun subscribe(dataTypeNames: Set<String>): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        if (dataTypeNames.isEmpty()) return 0
        return try {
            val dataTypeClass = Class.forName(DATA_TYPE_CLASS)
            val enumClass = dataTypeClass as Class<out Enum<*>>
            val list = ArrayList<Any>(dataTypeNames.size)
            dataTypeNames.forEach { name ->
                val enumValue = java.lang.Enum.valueOf(enumClass, name)
                list.add(enumValue)
            }
            synchronized(nativeLock) {
                subscribeMethod?.invoke(engineInstance, list) as? Int
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun unSubscribe(dataTypeNames: Set<String>): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        if (dataTypeNames.isEmpty()) return 0
        return try {
            val dataTypeClass = Class.forName(DATA_TYPE_CLASS)
            val enumClass = dataTypeClass as Class<out Enum<*>>
            val list = ArrayList<Any>(dataTypeNames.size)
            dataTypeNames.forEach { name ->
                val enumValue = java.lang.Enum.valueOf(enumClass, name)
                list.add(enumValue)
            }
            synchronized(nativeLock) {
                unSubscribeMethod?.invoke(engineInstance, list) as? Int
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun unsubscribeUnusedChannels() {
        val unusedTypes = setOf(
            "eMBCAN_DTC",
            "eMBCAN_UART_TEST_RESULT",
            "eMBCAN_USB_DEVICE",
            "eMBCAN_UPGRADE_PROGRESS",
            "eMBCAN_VEHICLE_CEM_FRAG",
            "eMBCAN_VEHICLE_ICM_FAULT_INFO",
            "eMBCAN_INSTRUMENT_CMDREPLY",
            "eMBCAN_VEHICLE_INVERTER_STATUS",
            "eMBCAN_CHARGING_RESERVE",
            "eMBCAN_VEHICLE_EBS_SOC",
            "eMBCAN_CFG_DMS",
            "eMBCAN_DVR_STATUS",
            "eMBCAN_VEHICLE_DVR_PARAM",
            "eMBCAN_RADIO_PROGRAMSTATE",
        )
        val res = unSubscribe(unusedTypes)
        Log.i(LOG_TAG, "unsubscribeUnusedChannels: unsubscribed ${unusedTypes.size} channels, res=$res")
    }

    /**
     * Single [com.mengbo.mbCan.interfaces.IMBCanSettingsCallback] on [MBCanEngine] — forwards speed/engine
     * pushes into [MbCanRepository]. Safe to call once after [ensureInitialized]; no-op if already registered.
     */
    @Synchronized
    fun registerSettingsTelemetryBridge() {
        if (settingsTelemetryProxy != null) return
        if (ensureInitialized() !is MbCanAvailability.Available) return
        val inst = engineInstance ?: return
        val iface = try {
            Class.forName("com.mengbo.mbCan.interfaces.IMBCanSettingsCallback")
        } catch (_: Throwable) {
            return
        }
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _: Any?, method: Method, args: Array<out Any?>? ->
            vad.dashing.tbox.DclLogRecorder.recordCanRx()
            if (DclLogRecorder.isLoggingActive()) {
                DclLogRecorder.log("CAN_RX_MBCAN", "event=${method.name}")
            }
            when (method.name) {
                "onCanVehicleSpeed" -> {
                    val fromArgs = runCatching {
                        when (val raw = args?.getOrNull(0)) {
                            is Number -> raw.toFloat()
                            else -> {
                                if (raw == null) null
                                else {
                                    val getter = getCachedZeroArgMethod(raw.javaClass, "getSpeed")
                                    (getter?.invoke(raw) as? Number)?.toFloat()
                                }
                            }
                        }
                    }.getOrNull()
                    val speed = fromArgs ?: readVehicleSpeed()
                    val prev = lastSpeed
                    if (prev != null && speed != null && Math.abs(speed - prev) < 0.05f) {
                        return@InvocationHandler null
                    }
                    lastSpeed = speed
                    MbCanRepository.scheduleCarSpeedPush(speed)
                }
                "onVehicleEngineStatusChange" -> {
                    val engine = args?.getOrNull(0)
                    if (engine != null) {
                        val rpm = runCatching {
                            val getter = getCachedZeroArgMethod(engine.javaClass, "getfSpeed")
                            (getter?.invoke(engine) as? Number)?.toFloat()
                        }.getOrNull()
                        val temperature = runCatching {
                            val getter = getCachedZeroArgMethod(engine.javaClass, "getfTemperture")
                            (getter?.invoke(engine) as? Number)?.toFloat()
                        }.getOrNull()
                        if (rpm == lastEngineRpm && temperature == lastEngineTemp) {
                            return@InvocationHandler null
                        }
                        lastEngineRpm = rpm
                        lastEngineTemp = temperature
                        MbCanRepository.scheduleEngineRpmPush(rpm)
                        MbCanRepository.scheduleEngineTemperaturePush(temperature)
                    }
                }
                "onCanVehicleTires" -> {
                    // Same push as OEM carsettings MBTiresView (WarningSts / pressure / temp).
                    LauncherTireRepository.applyFromMbCan(args?.getOrNull(0))
                }
                "onVehicleIcmFaultInfoChange" -> {
                    LauncherVehicleAlertsRepository.applyIcmFaultInfo(args?.getOrNull(0))
                }
                "onVehicleBcmStatusChange" -> {
                    val bcm = args?.getOrNull(0)
                    if (bcm != null) {
                        val lightSts = runCatching {
                            getCachedZeroArgMethod(bcm.javaClass, "getLightStatus")?.invoke(bcm)
                        }.getOrNull()
                        val low = if (lightSts != null) {
                            runCatching {
                                (getCachedZeroArgMethod(lightSts.javaClass, "getLowBeamSts")?.invoke(lightSts) as? Number)?.toInt()
                            }.getOrNull() ?: 0
                        } else 0
                        val high = if (lightSts != null) {
                            runCatching {
                                (getCachedZeroArgMethod(lightSts.javaClass, "getHighBeamSts")?.invoke(lightSts) as? Number)?.toInt()
                            }.getOrNull() ?: 0
                        } else 0
                        val hmaVal = runCatching {
                            (getCachedZeroArgMethod(bcm.javaClass, "getHMA_Status")?.invoke(bcm) as? Number)?.toInt()
                        }.getOrNull() ?: 0

                        val key = (if (low > 0) 1 else 0) or (if (high > 0) 2 else 0) or (if (hmaVal > 0) 4 else 0)
                        if (lastBcmLightKey == key) {
                            return@InvocationHandler null
                        }
                        lastBcmLightKey = key

                        MbCanRepository.scheduleBcmLightPush(
                            lowBeam = low > 0,
                            highBeam = high > 0,
                            hmaOn = hmaVal > 0,
                        )
                    }
                }
                "onVehicleConsumptionChange" -> {
                    // Штатный расход с CAN игнорируется: расчёт среднего расхода идет только по пройденному расстоянию и потраченному бензину
                }
            }
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
        settingsTelemetryProxy = proxy
        try {
            registerCarSettingsListenerMethod?.invoke(inst, proxy)
        } catch (_: Throwable) {
            settingsTelemetryProxy = null
        }
        registerAirPurgeBridge()
    }

    @Synchronized
    fun unregisterSettingsTelemetryBridge() {
        unregisterAirPurgeBridge()
        val inst = engineInstance
        if (inst != null && settingsTelemetryProxy != null) {
            try {
                unregisterCarSettingsListenerMethod?.invoke(inst)
            } catch (_: Throwable) {
            }
        }
        settingsTelemetryProxy = null
        LauncherTireRepository.clear()
    }

    @Synchronized
    fun registerAirPurgeBridge() {
        if (airPurgeListenerProxy != null) return
        if (ensureInitialized() !is MbCanAvailability.Available) return
        val inst = engineInstance ?: return
        val iface = try {
            Class.forName("com.mengbo.mbCan.interfaces.IMBAirPurgeListener")
        } catch (_: Throwable) {
            return
        }
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _: Any?, method: Method, args: Array<out Any?>? ->
            if (method.name == "onPMChanged" && args != null && args.isNotEmpty()) {
                val pm25Obj = args[0]
                if (pm25Obj != null) {
                    val sts = runCatching {
                        val getter = pm25Obj.javaClass.getMethod("getPM25Sts")
                        (getter.invoke(pm25Obj) as? Number)?.toInt()
                    }.getOrNull()
                    val state = when (sts) {
                        2 -> MbCanBinaryState.On
                        1 -> MbCanBinaryState.Off
                        else -> MbCanBinaryState.Unknown
                    }
                    MbCanRepository.updateHvacPm25StateFromCan(state)

                    val inside = runCatching {
                        val getter = pm25Obj.javaClass.getMethod("getPM25Indensity")
                        (getter.invoke(pm25Obj) as? Number)?.toInt()?.toUInt()
                    }.getOrNull()
                    val outside = runCatching {
                        val getter = pm25Obj.javaClass.getMethod("getPM25outdensity")
                        (getter.invoke(pm25Obj) as? Number)?.toInt()?.toUInt()
                    }.getOrNull()

                    if (inside != null && inside > 0u && inside < 65535u) {
                        CanDataRepository.updateInsideAirQuality(inside)
                    }
                    if (outside != null && outside > 0u && outside < 65535u) {
                        CanDataRepository.updateOutsideAirQuality(outside)
                    }
                }
            }
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
        airPurgeListenerProxy = proxy
        try {
            registAirPurgeListenerMethod?.invoke(inst, proxy)
            Log.i(LOG_TAG, "registerAirPurgeBridge: registered IMBAirPurgeListener")
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "registerAirPurgeBridge failed", t)
            airPurgeListenerProxy = null
        }
    }

    @Synchronized
    fun unregisterAirPurgeBridge() {
        val inst = engineInstance
        if (inst != null && airPurgeListenerProxy != null) {
            try {
                unregistAirPurgeListenerMethod?.invoke(inst)
            } catch (_: Throwable) {
            }
        }
        airPurgeListenerProxy = null
    }

    /**
     * Registers a single [com.mengbo.mbCan.interfaces.IMBCmdListener] for [eMBCAN_CFG_VEHICLE] when [active],
     * unregisters when inactive. OEM [unRegistCMDListener] clears all listeners for that data type.
     */
    @Synchronized
    fun syncVehicleCfgCmdListener(active: Boolean) {
        if (!active) {
            unregisterVehicleCfgCmdListener()
            return
        }
        if (vehicleCfgCmdListenerProxy != null) return
        if (ensureInitialized() !is MbCanAvailability.Available) return
        val inst = engineInstance ?: return
        val dt = cfgVehicleDataType ?: return
        val iface = try {
            Class.forName("com.mengbo.mbCan.interfaces.IMBCmdListener")
        } catch (_: Throwable) {
            return
        }
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _: Any?, method: Method, args: Array<out Any?>? ->
            vad.dashing.tbox.DclLogRecorder.recordCanRx()
            if (method.name == "onCmdChanged" && args != null && args.size >= 4) {
                val modular = (args[0] as Number).toInt() and 0xFF
                val rev = (args[1] as Number).toInt() and 0xFF
                val item = (args[2] as Number).toInt() and 0xFFFF
                val value = (args[3] as Number).toInt()
                val prev = lastVehicleCfgValues[item]
                if (prev != null && prev == value) {
                    return@InvocationHandler null
                }
                lastVehicleCfgValues[item] = value
                if (DclLogRecorder.isLoggingActive() || MbCanDiagnostics.enabled.value) {
                    MbCanDiagnostics.log(
                        "DEBUG",
                        "cfgVehiclePush modular=$modular rev=$rev item=$item value=$value"
                    )
                }
                MbCanRepository.scheduleVehicleCfgPush(modular, item, value)
            }
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
        vehicleCfgCmdListenerProxy = proxy
        try {
            registCmdListenerMethod?.invoke(inst, dt, proxy)
        } catch (_: Throwable) {
            vehicleCfgCmdListenerProxy = null
        }
    }

    @Synchronized
    private fun unregisterVehicleCfgCmdListener() {
        val inst = engineInstance
        val dt = cfgVehicleDataType
        if (inst != null && vehicleCfgCmdListenerProxy != null && dt != null) {
            try {
                unRegistCmdListenerMethod?.invoke(inst, dt)
            } catch (_: Throwable) {
            }
        }
        vehicleCfgCmdListenerProxy = null
    }

    /**
     * Registers [IMBCmdListener] for [eMBCAN_CFG_AUDIO] (same [onCmdChanged] shape as vehicle cfg).
     * Independent from [syncVehicleCfgCmdListener]; OEM clears listeners per data type on unregister.
     */
    @Synchronized
    fun syncAudioCfgCmdListener(active: Boolean) {
        if (!active) {
            unregisterAudioCfgCmdListener()
            return
        }
        if (audioCfgCmdListenerProxy != null) return
        if (ensureInitialized() !is MbCanAvailability.Available) return
        val inst = engineInstance ?: return
        val dt = cfgAudioDataType ?: return
        val iface = try {
            Class.forName("com.mengbo.mbCan.interfaces.IMBCmdListener")
        } catch (_: Throwable) {
            return
        }
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _: Any?, method: Method, args: Array<out Any?>? ->
            vad.dashing.tbox.DclLogRecorder.recordCanRx()
            if (method.name == "onCmdChanged" && args != null && args.size >= 4) {
                val modular = (args[0] as Number).toInt() and 0xFF
                val rev = (args[1] as Number).toInt() and 0xFF
                val item = (args[2] as Number).toInt() and 0xFFFF
                val value = (args[3] as Number).toInt()
                val prev = lastAudioCfgValues[item]
                if (prev != null && prev == value) {
                    return@InvocationHandler null
                }
                lastAudioCfgValues[item] = value
                if (DclLogRecorder.isLoggingActive() || MbCanDiagnostics.enabled.value) {
                    MbCanDiagnostics.log(
                        "DEBUG",
                        "cfgAudioPush modular=$modular rev=$rev item=$item value=$value"
                    )
                }
                MbCanRepository.scheduleAudioCfgPush(modular, item, value)
            }
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
        audioCfgCmdListenerProxy = proxy
        try {
            registCmdListenerMethod?.invoke(inst, dt, proxy)
        } catch (_: Throwable) {
            audioCfgCmdListenerProxy = null
        }
    }

    /**
     * Reads RPM from [com.mengbo.mbCan.entity.MBCanVehicleEngine#getfSpeed()] via
     * [com.mengbo.mbCan.MBCanEngine.getMbCanData] data type 22 (eMBCAN_VEHICLE_ENGINE).
     */
    fun readVehicleEngineRpm(): Float? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            withNativeLock {
                val engineClass = Class.forName(ENGINE_CLASS)
                val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
                val engCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleEngine")
                val engObj = getMbCanData.invoke(inst, 22, engCls) ?: return null
                val fs = engCls.getMethod("getfSpeed").invoke(engObj) as? Number
                fs?.toFloat()
            }
        }.getOrNull()
    }

    /**
     * Reads coolant temperature from [com.mengbo.mbCan.entity.MBCanVehicleEngine#getfTemperture()].
     */
    fun readVehicleEngineTemperature(): Float? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            withNativeLock {
                val engineClass = Class.forName(ENGINE_CLASS)
                val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
                val engCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleEngine")
                val engObj = getMbCanData.invoke(inst, 22, engCls) ?: return null
                val temp = engCls.getMethod("getfTemperture").invoke(engObj) as? Number
                temp?.toFloat()
            }
        }.getOrNull()
    }

    /**
     * Reads speed from [com.mengbo.mbCan.entity.MBCanVehicleSpeed#getSpeed()] via
     * [com.mengbo.mbCan.MBCanEngine.getMbCanData] data type 1 (eMBCAN_VEHICLE_SPEED).
     */
    fun readVehicleSpeed(): Float? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            withNativeLock {
                val engineClass = Class.forName(ENGINE_CLASS)
                val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
                val speedCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleSpeed")
                val speedObj = getMbCanData.invoke(inst, 1, speedCls) ?: return null
                val speed = speedCls.getMethod("getSpeed").invoke(speedObj) as? Number
                speed?.toFloat()
            }
        }.getOrNull()
    }

    /**
     * Reads door/trunk state from [com.mengbo.mbCan.defines.MBCanDataType.eMBCAN_VEHICLE_DOOR] (5).
     * Returns null when mbCAN is unavailable or data is missing.
     */
    fun readVehicleDoor(): LauncherVehicleDoorSnapshot? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            withNativeLock {
                val engineClass = Class.forName(ENGINE_CLASS)
                val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
                val doorCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleDoor")
                val doorObj = getMbCanData.invoke(inst, 5, doorCls) ?: return null
                LauncherVehicleDoorSnapshot(
                    driverDoor = doorCls.getMethod("getDriverDoorSts").invoke(doorObj) as Byte,
                    passengerDoor = doorCls.getMethod("getPsngrDoorSts").invoke(doorObj) as Byte,
                    rearLeftDoor = doorCls.getMethod("getLHRdoorSts").invoke(doorObj) as Byte,
                    rearRightDoor = doorCls.getMethod("getRHRDoorSts").invoke(doorObj) as Byte,
                    trunk = doorCls.getMethod("getTrunkSts").invoke(doorObj) as Byte,
                    hood = doorCls.getMethod("getHoodSts").invoke(doorObj) as Byte,
                )
            }
        }.getOrNull()
    }

    @Synchronized
    private fun unregisterAudioCfgCmdListener() {
        val inst = engineInstance
        val dt = cfgAudioDataType
        if (inst != null && audioCfgCmdListenerProxy != null && dt != null) {
            try {
                unRegistCmdListenerMethod?.invoke(inst, dt)
            } catch (_: Throwable) {
            }
        }
        audioCfgCmdListenerProxy = null
    }

    /**
     * Debug snapshot from [com.mengbo.mbCan.MBCanEngine.getMbCanData] (native cache only; no CycleData).
     * 1 = [com.mengbo.mbCan.defines.MBCanDataType.eMBCAN_VEHICLE_SPEED],
     * 22 / 29 = [com.mengbo.mbCan.defines.MBCanDataType.eMBCAN_VEHICLE_ENGINE] /
     * [com.mengbo.mbCan.defines.MBCanDataType.eMBCAN_VEHICLE_ENGINE_GEAR] ([MBCanVehicleEngine]; `fs` is vendor field name, may correlate to RPM on HU).
     */
    fun peekMbCanMotionDebugLine(): String {
        if (availabilityRef.get() !is MbCanAvailability.Available) return "mbCAN_motion=na"
        val inst = engineInstance ?: return "mbCAN_motion=no_inst"
        return try {
            withNativeLock {
                val engineClass = Class.forName(ENGINE_CLASS)
                val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
                val spdCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleSpeed")
                val spdObj = getMbCanData.invoke(inst, 1, spdCls)
                val speedStr =
                    if (spdObj != null) {
                        val s = spdCls.getMethod("getSpeed").invoke(spdObj) as Float
                        val ok = spdCls.getMethod("getSpeedValidSts").invoke(spdObj) as Byte
                        "mbCAN_dt1_spd=$s ok=$ok"
                    } else {
                        "mbCAN_dt1_spd=null"
                    }
                val engCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleEngine")
                fun fmtEng(prefix: String, dataType: Int): String {
                    val engObj = getMbCanData.invoke(inst, dataType, engCls)
                    return if (engObj != null) {
                        val fs = engCls.getMethod("getfSpeed").invoke(engObj) as Float
                        val tmp = engCls.getMethod("getfTemperture").invoke(engObj) as Float
                        val st = engCls.getMethod("getStatus").invoke(engObj) as Byte
                        val dsp = engCls.getMethod("getnDisplayVehiceSpeed").invoke(engObj) as Short
                        "${prefix}fs=$fs tmp=$tmp st=$st dsp=$dsp"
                    } else {
                        "${prefix}null"
                    }
                }
                val eng22 = fmtEng("mbCAN_dt22_eng ", 22)
                val eng29 = fmtEng("mbCAN_dt29_eg ", 29)
                listOf(speedStr, eng22, eng29).joinToString(" | ")
            }
        } catch (t: Throwable) {
            "mbCAN_motion_err=${t.javaClass.simpleName}:${t.message}"
        }
    }

    /**
     * Registers [IMBHardKeyListener] for steering wheel / hardware key events.
     */
    @Synchronized
    fun setHardKeyCallback(callback: ((keyCode: Int, keyAction: Int, extra: Byte) -> Unit)?) {
        hardKeyCallback = callback
        if (callback == null) {
            unregisterHardKeyListener()
            return
        }
        if (hardKeyListenerProxy != null) return
        if (ensureInitialized() !is MbCanAvailability.Available) return
        val inst = engineInstance ?: return
        val iface = try {
            Class.forName("com.mengbo.mbCan.interfaces.IMBHardKeyListener")
        } catch (_: Throwable) {
            return
        }
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _: Any?, method: Method, args: Array<out Any?>? ->
            vad.dashing.tbox.DclLogRecorder.recordCanRx()
            if (method.name == "onHardKey" && args != null && args.size >= 3) {
                val keyCode = (args[0] as Number).toInt()
                val keyAction = (args[1] as Number).toInt()
                val extra = (args[2] as Number).toByte()
                hardKeyCallback?.invoke(keyCode, keyAction, extra)
            }
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
        hardKeyListenerProxy = proxy
        try {
            registHardKeyListenerMethod?.invoke(inst, proxy)
            Log.i(LOG_TAG, "setHardKeyCallback: registered IMBHardKeyListener")
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "setHardKeyCallback failed: ${t.message}")
            hardKeyListenerProxy = null
        }
    }

    @Synchronized
    private fun unregisterHardKeyListener() {
        val inst = engineInstance
        if (inst != null && hardKeyListenerProxy != null) {
            try {
                unRegistHardKeyListenerMethod?.invoke(inst)
                Log.i(LOG_TAG, "unregisterHardKeyListener: unregistered IMBHardKeyListener")
            } catch (_: Throwable) {
            }
        }
        hardKeyListenerProxy = null
    }
}

