package vad.dashing.tbox.mbcan

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import dalvik.system.DexClassLoader
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import vad.dashing.tbox.AppContextHolder

/**
 * Reflection bridge to OEM [com.mengbo.adas.MBAdasEngine] TSR stream
 * (`MBCanTrafficSignRecognition` / MBCAN_ADAS_TSR=4).
 *
 * Does not ship ADAS Java stubs — loads classes from the app ClassLoader or
 * an OEM APK (acsettings / launcher) so native `mbAdas` stays optional.
 */
object MbAdasTsrFacade {
    private const val TAG = "MbAdasTsr"
    private const val ENGINE_CLASS = "com.mengbo.adas.MBAdasEngine"
    private const val LISTENER_CLASS = "com.mengbo.adas.MBAdasEngine\$IAdasDataListener"
    private const val TSR_ENTITY = "com.mengbo.adas.entity.MBCanTrafficSignRecognition"

    private val oemPackages = listOf(
        "com.mengbo.acsettings",
        "com.wt.launcher3",
        "com.mengbo.mbcanwidget",
    )

    @Volatile
    private var active = false
    private var engineInstance: Any? = null
    private var listenerProxy: Any? = null
    private var engineClassLoader: ClassLoader? = null
    private var onTsr: ((TsrRaw) -> Unit)? = null

    data class TsrRaw(
        val valueRaw: Byte,
        val unitRaw: Byte,
        val confidence: Float,
        val signClass: Byte,
        val posX: Float?,
        val posY: Float?,
    )

    @Synchronized
    fun ensureListening(onUpdate: (TsrRaw) -> Unit): Boolean {
        onTsr = onUpdate
        if (active && listenerProxy != null) return true
        val context = AppContextHolder.appContextOrNull ?: return false
        return try {
            val loader = resolveClassLoader(context) ?: return false
            engineClassLoader = loader
            val engineClass = Class.forName(ENGINE_CLASS, true, loader)
            val listenerClass = Class.forName(LISTENER_CLASS, true, loader)
            val instance = engineClass.getMethod("getInstance").invoke(null) ?: return false
            val proxyHolder = arrayOfNulls<Any>(1)
            val handler = InvocationHandler { _, method, args ->
                when (method.name) {
                    "onTrafficSignRecognitionUpdate" -> {
                        if (args != null && args.isNotEmpty()) {
                            parseTsr(args[0], loader)?.let { onTsr?.invoke(it) }
                        }
                    }
                    "equals" -> return@InvocationHandler (args?.getOrNull(0) === proxyHolder[0])
                    "hashCode" -> return@InvocationHandler System.identityHashCode(proxyHolder[0] ?: this)
                    "toString" -> return@InvocationHandler "TBoxTsrAdasListener"
                }
                when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    java.lang.Float.TYPE -> 0f
                    java.lang.Double.TYPE -> 0.0
                    else -> null
                }
            }
            val proxy = Proxy.newProxyInstance(loader, arrayOf(listenerClass), handler)
            proxyHolder[0] = proxy
            val ok = engineClass
                .getMethod("registerAdasDataListener", listenerClass)
                .invoke(instance, proxy) as? Boolean
                ?: false
            if (ok) {
                engineInstance = instance
                listenerProxy = proxy
                active = true
                Log.i(TAG, "TSR listener registered")
            } else {
                Log.w(TAG, "registerAdasDataListener returned false")
            }
            ok
        } catch (t: Throwable) {
            Log.w(TAG, "TSR bridge unavailable: ${t.javaClass.simpleName}: ${t.message}")
            active = false
            false
        }
    }

    @Synchronized
    fun stop() {
        val inst = engineInstance
        val proxy = listenerProxy
        val loader = engineClassLoader
        onTsr = null
        if (inst != null && proxy != null && loader != null) {
            runCatching {
                val engineClass = Class.forName(ENGINE_CLASS, false, loader)
                val listenerClass = Class.forName(LISTENER_CLASS, false, loader)
                engineClass.getMethod("unRegisterAdasDataListener", listenerClass)
                    .invoke(inst, proxy)
            }
        }
        engineInstance = null
        listenerProxy = null
        engineClassLoader = null
        active = false
    }

    private fun resolveClassLoader(context: Context): ClassLoader? {
        // 1) App classpath (if OEM jars ever bundled / shared).
        runCatching {
            Class.forName(ENGINE_CLASS, false, MbAdasTsrFacade::class.java.classLoader)
            return MbAdasTsrFacade::class.java.classLoader
        }
        // 2) Package context with code (same sharedUserId / system).
        for (pkg in oemPackages) {
            runCatching {
                val oem = context.createPackageContext(
                    pkg,
                    Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
                )
                Class.forName(ENGINE_CLASS, false, oem.classLoader)
                return oem.classLoader
            }
        }
        // 3) DexClassLoader from OEM APK path.
        val pm = context.packageManager
        val codeCache = context.codeCacheDir.absolutePath
        for (pkg in oemPackages) {
            runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                val loader = DexClassLoader(
                    info.sourceDir,
                    codeCache,
                    info.nativeLibraryDir,
                    MbAdasTsrFacade::class.java.classLoader,
                )
                Class.forName(ENGINE_CLASS, true, loader)
                return loader
            }.onFailure {
                Log.d(TAG, "Dex load $pkg failed: ${it.message}")
            }
        }
        return null
    }

    private fun parseTsr(raw: Any?, loader: ClassLoader): TsrRaw? = runCatching {
        if (raw == null) return null
        val cls = Class.forName(TSR_ENTITY, false, loader)
        val value = cls.getMethod("getSpdLimitValue").invoke(raw) as Byte
        val unit = cls.getMethod("getSpdLimitUnit").invoke(raw) as Byte
        val conf = (cls.getMethod("getfSpdLimitConfi").invoke(raw) as? Number)?.toFloat() ?: 0f
        val signClass = cls.getMethod("getSpdLimitClass").invoke(raw) as Byte
        var posX: Float? = null
        var posY: Float? = null
        runCatching {
            val pos = cls.getMethod("getPosition").invoke(raw)
            if (pos != null) {
                val pCls = pos.javaClass
                posX = (pCls.getMethod("getPointX").invoke(pos) as? Number)?.toFloat()
                posY = (pCls.getMethod("getPointY").invoke(pos) as? Number)?.toFloat()
            }
        }
        TsrRaw(value, unit, conf, signClass, posX, posY)
    }.getOrNull()
}

/** Clears stale TSR after [timeoutMs] without updates (main looper). */
internal class TsrStaleWatchdog(
    private val timeoutMs: Long = 4_000L,
    private val onStale: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val clearRunnable = Runnable { onStale() }

    fun kick() {
        handler.removeCallbacks(clearRunnable)
        handler.postDelayed(clearRunnable, timeoutMs)
    }

    fun cancel() {
        handler.removeCallbacks(clearRunnable)
    }
}
