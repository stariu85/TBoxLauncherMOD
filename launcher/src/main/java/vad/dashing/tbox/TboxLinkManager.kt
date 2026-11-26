package vad.dashing.tbox

import android.content.Context
import android.os.SystemClock
import android.util.Log
import dashingineering.jetour.tboxcore.TBoxClient
import dashingineering.jetour.tboxcore.types.LogType
import dashingineering.jetour.tboxcore.types.TBoxClientCallback
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import vad.dashing.tbox.utils.CanFramesProcess

/**
 * Lightweight TBox UDP link via [tbox-proxy] [TBoxClient].
 *
 * The library owns [TBoxBridgeService] (UDP 50047). If Monitor (`vad.dashing.tbox`) is also
 * installed, both apps attach to the same bridge over IPC; otherwise the launcher starts it.
 */
class TboxLinkManager(
    private val appContext: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val packetDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(
            {
                vad.dashing.tbox.ui.launcher.LauncherCpuAffinity
                    .applyOptionalServiceBackgroundAffinityOnCurrentThread("launcher-tbox-packets")
                r.run()
            },
            "launcher-tbox-packets",
        ).apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val packetChannel = Channel<ByteArray>(
        capacity = Channel.BUFFERED,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val sendMutex = Mutex()
    private val clientLifecycleLock = Any()

    @Volatile
    private var client: TBoxClient? = null
    private var packetConsumerJob: Job? = null
    private var netPollJob: Job? = null
    private var canResubJob: Job? = null
    private var silenceWatchJob: Job? = null
    private var reconnectJob: Job? = null
    @Volatile private var lastPacketAtMs = 0L
    private val packetSilenceChecks = AtomicInteger(0)
    private val netMisses = AtomicInteger(0)

    private fun nowElapsedMs(): Long = try {
        SystemClock.elapsedRealtime()
    } catch (_: Throwable) {
        System.currentTimeMillis()
    }

    fun start() {
        startPacketConsumer()
        connect()
        startNetPoll()
        startCanResubscribe()
        startSilenceWatch()
        startReconnectWatch()
    }

    fun stop() {
        reconnectJob?.cancel()
        netPollJob?.cancel()
        canResubJob?.cancel()
        silenceWatchJob?.cancel()
        packetConsumerJob?.cancel()
        packetChannel.close()
        scope.cancel()
        disconnect()
        packetDispatcher.close()
    }

    private fun startPacketConsumer() {
        if (packetConsumerJob?.isActive == true) return
        packetConsumerJob = scope.launch(packetDispatcher) {
            for (data in packetChannel) {
                try {
                    handlePacket(data)
                    if (!TboxRepository.tboxConnected.value) {
                        onConnected(true)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    TboxRepository.addLog("ERROR", "TBox Proxy", "Error handling incoming data")
                    Log.e(TAG, "Error handling incoming data", e)
                }
            }
        }
    }

    private fun connect() {
        synchronized(clientLifecycleLock) {
            if (client != null) return
            val remoteIp = DEFAULT_TBOX_IP
            val callback = object : TBoxClientCallback {
                override fun onDataReceived(data: ByteArray) {
                    lastPacketAtMs = nowElapsedMs()
                    packetChannel.trySend(data)
                }

                override fun onLogMessage(type: LogType, tag: String, message: String) {
                    when (type) {
                        LogType.ERROR -> TboxRepository.addLog("ERROR", "TBox Proxy/$tag", message)
                        LogType.WARN -> TboxRepository.addLog("WARN", "TBox Proxy/$tag", message)
                        else -> Unit
                    }
                }

                override fun onConnectionChanged(connected: Boolean) {
                    TboxRepository.addLog(
                        "INFO",
                        "TBox Proxy",
                        "Bridge connection state: ${if (connected) "connected" else "disconnected"}",
                    )
                    if (!connected && TboxRepository.tboxConnected.value) {
                        onConnected(false)
                    }
                }
            }
            try {
                client = TBoxClient(
                    context = appContext,
                    remotePort = SERVER_PORT,
                    remoteAddress = remoteIp,
                    callback = callback,
                ).also { it.initialize() }
                lastPacketAtMs = nowElapsedMs()
                TboxRepository.addLog("INFO", "TBox Proxy", "Client initialized for $remoteIp")
            } catch (e: Exception) {
                TboxRepository.addLog("ERROR", "TBox Proxy", "Failed to initialize client for $remoteIp")
                Log.e(TAG, "Failed to initialize client", e)
                client = null
            }
        }
    }

    private fun disconnect() {
        synchronized(clientLifecycleLock) {
            try {
                client?.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to destroy client", e)
            } finally {
                client = null
            }
        }
    }

    private fun startNetPoll() {
        if (netPollJob?.isActive == true) return
        netPollJob = scope.launch {
            delay(2_000)
            while (isActive) {
                val ok = send(MDC_CODE, 0x07, byteArrayOf(0x01, 0x00), needLog = false)
                if (!ok) {
                    if (netMisses.incrementAndGet() > 2) {
                        TboxRepository.updateNetState(NetState())
                    }
                }
                delay(NET_POLL_MS)
            }
        }
    }

    private fun startCanResubscribe() {
        if (canResubJob?.isActive == true) return
        canResubJob = scope.launch {
            while (isActive) {
                if (TboxRepository.tboxConnected.value) {
                    requestCanFrames()
                    requestLocSubscribe()
                }
                delay(CAN_RESUB_MS)
            }
        }
    }

    private fun startSilenceWatch() {
        if (silenceWatchJob?.isActive == true) return
        silenceWatchJob = scope.launch {
            while (isActive) {
                delay(NET_POLL_MS)
                if (!TboxRepository.tboxConnected.value) continue
                val silentFor = nowElapsedMs() - lastPacketAtMs
                if (silentFor > NET_POLL_MS * 2) {
                    if (packetSilenceChecks.incrementAndGet() >= 3) {
                        onConnected(false)
                    }
                } else {
                    packetSilenceChecks.set(0)
                }
            }
        }
    }

    private fun startReconnectWatch() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            while (isActive) {
                delay(RECONNECT_MS)
                if (client == null) {
                    TboxRepository.addLog("INFO", "TBox Proxy", "Reconnecting…")
                    connect()
                } else if (!TboxRepository.tboxConnected.value) {
                    // Soft bounce: destroy + recreate when bridge is up but TBox is silent.
                    val silentFor = nowElapsedMs() - lastPacketAtMs
                    if (silentFor > RECONNECT_MS) {
                        disconnect()
                        connect()
                    }
                }
            }
        }
    }

    private fun onConnected(connected: Boolean) {
        packetSilenceChecks.set(0)
        if (connected) {
            TboxRepository.addLog("INFO", "TBox connection", "TBox connected")
            TboxRepository.updateTboxConnected(true)
            TboxRepository.updateTboxConnectionTime()
            scope.launch {
                requestCanFrames()
                requestLocSubscribe()
            }
        } else {
            TboxRepository.addLog("WARN", "TBox connection", "TBox disconnected")
            TboxRepository.resetConnectionData()
            CanDataRepository.resetConnectionData()
        }
    }

    private suspend fun requestCanFrames() {
        send(CRT_CODE, 0x15, byteArrayOf(0x01, 0x02), needLog = false)
    }

    private suspend fun requestLocSubscribe() {
        send(LOC_CODE, 0x05, byteArrayOf(0x02, LOC_PERIOD_S, 0x00), needLog = false)
    }

    private suspend fun send(
        tid: Byte,
        cmd: Byte,
        payload: ByteArray,
        needLog: Boolean = true,
    ): Boolean {
        return try {
            var data = fillHeader(payload.size, tid, SELF_CODE, cmd) + payload
            data += xorSum(data)
            val sent = sendMutex.withLock {
                val c = client ?: return@withLock false
                withTimeout(1_000) { c.sendRawMessage(data) }
                true
            }
            if (!sent) return false
            if (needLog) {
                TboxRepository.addLog("DEBUG", "Proxy message send", toHexString(data))
            }
            true
        } catch (e: TimeoutCancellationException) {
            TboxRepository.addLog("ERROR", "Proxy message send", "Timeout sending message")
            Log.w(TAG, "send timed out", e)
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "Proxy message send", "Error send message")
            Log.e(TAG, "send failed", e)
            false
        }
    }

    private fun handlePacket(raw: ByteArray) {
        if (!checkPacket(raw)) return
        val dataLength = extractDataLength(raw)
        if (!checkLength(raw, dataLength)) return
        val payload = extractData(raw, dataLength) ?: return
        if (payload.isEmpty()) return

        // Byte 9 = SID of response (= module that replied), same as Monitor BackgroundService.
        val module = raw[9]
        val cmd = raw[12]
        when (module) {
            MDC_CODE -> if (cmd == 0x87.toByte()) parseMdcNetState(payload)
            CRT_CODE -> {
                if (cmd == 0x95.toByte()) {
                    parseCrtCanFrame(payload)
                } else if (cmd == 0x81.toByte() || cmd == 0x84.toByte() || cmd == 0x85.toByte() || payload.size >= 342) {
                    parseCrtCycleSignal(payload)
                }
            }
            LOC_CODE -> if (cmd == 0x85.toByte()) parseLocValues(payload)
            else -> Unit
        }
    }

    private fun parseMdcNetState(data: ByteArray) {
        if (data.size < 8) return
        if (data.copyOfRange(0, 4).contentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())) ||
            data.copyOfRange(0, 4).contentEquals(byteArrayOf(0xF4.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())) ||
            data.copyOfRange(0, 4).contentEquals(byteArrayOf(0xF5.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            return
        }
        netMisses.set(0)
        val regStatus = when (data[4].toInt()) {
            0 -> "нет"
            1 -> "домашняя сеть"
            2 -> "поиск сети"
            3 -> "регистрация отклонена"
            5 -> "роуминг"
            else -> "${data[4]}"
        }
        val csq = if (data[5] == 0x99.toByte()) 99 else data[5].toInt() and 0xFF
        val signalLevel = signalLevelFromCsq(csq)
        val simStatus = when (data[6].toInt()) {
            0 -> "нет SIM"
            1 -> "SIM готова"
            2 -> "требуется PIN"
            3 -> "ошибка SIM"
            else -> "${data[6]}"
        }
        val netStatus = when (data[7].toInt()) {
            0 -> "-"
            2 -> "2G"
            3 -> "3G"
            4 -> "4G"
            7 -> "нет сети"
            else -> "${data[7]}"
        }
        val prev = TboxRepository.netState.value
        val connectionChangeTime = if (prev.regStatus != regStatus) Date() else prev.connectionChangeTime
        if (prev.csq != csq ||
            prev.signalLevel != signalLevel ||
            prev.netStatus != netStatus ||
            prev.regStatus != regStatus ||
            prev.simStatus != simStatus
        ) {
            TboxRepository.updateNetState(
                NetState(
                    csq = csq,
                    signalLevel = signalLevel,
                    netStatus = netStatus,
                    regStatus = regStatus,
                    simStatus = simStatus,
                    connectionChangeTime = connectionChangeTime,
                ),
            )
        }
    }

    private fun parseLocValues(data: ByteArray) {
        if (data.size < 45) return
        if (data.copyOfRange(0, 4).contentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))) {
            return
        }
        runCatching {
            val gpsData = data.copyOfRange(6, 45)
            val buffer = ByteBuffer.wrap(gpsData).order(ByteOrder.LITTLE_ENDIAN)
            val locateStatus = buffer.get().toInt() and 0xFF != 0
            val utcTime = UtcTime(
                year = buffer.get().toInt() and 0xFF,
                month = buffer.get().toInt() and 0xFF,
                day = buffer.get().toInt() and 0xFF,
                hour = buffer.get().toInt() and 0xFF,
                minute = buffer.get().toInt() and 0xFF,
                second = buffer.get().toInt() and 0xFF,
            )
            val longitudeDirection = buffer.get().toInt() and 0xFF
            val longitude = buffer.int.toDouble() / 1_000_000.0 * if (longitudeDirection == 1) -1 else 1
            val latitudeDirection = buffer.get().toInt() and 0xFF
            val latitude = buffer.int.toDouble() / 1_000_000.0 * if (latitudeDirection == 1) -1 else 1
            val altitude = buffer.int.toDouble() / 1_000_000.0
            val visibleSatellites = buffer.get().toInt() and 0xFF
            val usingSatellites = buffer.get().toInt() and 0xFF
            val speed = (buffer.short.toInt() and 0xFFFF) / 10f
            val trueDirection = (buffer.short.toInt() and 0xFFFF) / 10f
            val magneticDirection = (buffer.short.toInt() and 0xFFFF) / 10f
            val locValues = LocValues(
                locateStatus = locateStatus,
                utcTime = utcTime,
                longitude = longitude,
                latitude = latitude,
                altitude = altitude,
                visibleSatellites = visibleSatellites,
                usingSatellites = usingSatellites,
                speed = speed,
                trueDirection = trueDirection,
                magneticDirection = magneticDirection,
                updateTime = Date(),
            )
            TboxRepository.updateLocValues(locValues)
            TboxRepository.updateIsLocValuesTrue(locValues.hasGpsFix)
        }
    }

    private fun parseCrtCanFrame(data: ByteArray) {
        if (data.size < 4) return
        if (data[0] != 0.toByte() || data[1] != 0.toByte() || data[2] != 0.toByte() || data[3] != 0.toByte()) return
        try {
            CanFramesProcess.process(data, maxFrames = 20)
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "CRT response", "Error get CAN Frame: $e")
        }
    }

    private fun parseCrtCycleSignal(data: ByteArray) {
        try {
            CanFramesProcess.processCycleSignal(data)
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "CRT cycle response", "Error get cycle signal: $e")
        }
    }

    companion object {
        private const val TAG = "TboxLink"
        private const val DEFAULT_TBOX_IP = "192.168.225.1"
        private const val SERVER_PORT = 50047
        private const val NET_POLL_MS = 5_000L
        private const val CAN_RESUB_MS = 10_000L
        private const val RECONNECT_MS = 30_000L
        private val MDC_CODE = 0x25.toByte()
        private val CRT_CODE = 0x23.toByte()
        private val LOC_CODE = 0x29.toByte()
        private val SELF_CODE = 0x50.toByte()
        private const val LOC_PERIOD_S: Byte = 0x01

        private fun signalLevelFromCsq(csq: Int): Int = when {
            csq == 99 || csq < 0 -> 0
            csq <= 7 -> 1
            csq <= 14 -> 2
            csq <= 19 -> 3
            csq <= 24 -> 4
            else -> 5
        }
    }
}
