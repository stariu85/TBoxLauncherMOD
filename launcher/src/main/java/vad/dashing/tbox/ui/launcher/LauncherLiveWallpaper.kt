package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.core.graphics.scale
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import vad.dashing.tbox.toOwnedImageBitmap
import java.io.File
import java.util.concurrent.Executors

private const val TAG = "LauncherLiveWallpaper"
private const val WALLPAPER_MAX_DIM_PX = 1920
private const val WEBP_HEADER_SCAN_BYTES = 128

private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "webm", "mkv", "3gp", "mov")

internal enum class WallpaperMediaType {
    STATIC_IMAGE,
    ANIMATED_IMAGE,
    VIDEO,
}

internal data class VideoTransformScale(
    val scaleX: Float,
    val scaleY: Float,
)

internal data class DrawableMatrixParams(
    val scaleX: Float,
    val scaleY: Float,
    val translateX: Float,
    val translateY: Float,
)

/**
 * Pure media-type detection from path/extension, optional MIME type, and optional header bytes.
 */
internal fun detectWallpaperMediaType(
    filePath: String,
    mimeType: String? = null,
    headerBytes: ByteArray? = null,
): WallpaperMediaType {
    val cleanMime = mimeType?.trim()?.lowercase().orEmpty()
    if (cleanMime.startsWith("video/")) return WallpaperMediaType.VIDEO
    if (cleanMime == "image/gif") return WallpaperMediaType.ANIMATED_IMAGE
    if (cleanMime == "image/webp") {
        return if (isAnimatedWebpHeader(headerBytes)) {
            WallpaperMediaType.ANIMATED_IMAGE
        } else {
            WallpaperMediaType.STATIC_IMAGE
        }
    }

    val ext = filePath.substringAfterLast('.', "").lowercase()
    return when {
        ext in VIDEO_EXTENSIONS -> WallpaperMediaType.VIDEO
        ext == "gif" -> WallpaperMediaType.ANIMATED_IMAGE
        ext == "webp" -> {
            if (isAnimatedWebpHeader(headerBytes)) {
                WallpaperMediaType.ANIMATED_IMAGE
            } else {
                WallpaperMediaType.STATIC_IMAGE
            }
        }
        else -> WallpaperMediaType.STATIC_IMAGE
    }
}

/**
 * Inspects file extension and (for `.webp`) reads the first 128 bytes to check for animation chunks.
 */
internal fun detectWallpaperMediaTypeFromPath(filePath: String): WallpaperMediaType {
    if (filePath.isBlank()) return WallpaperMediaType.STATIC_IMAGE
    val ext = filePath.substringAfterLast('.', "").lowercase()
    if (ext != "webp") {
        return detectWallpaperMediaType(filePath)
    }
    val header = runCatching {
        val file = File(filePath)
        if (!file.isFile || file.length() <= 0L) return@runCatching null
        val buf = ByteArray(WEBP_HEADER_SCAN_BYTES)
        file.inputStream().use { input ->
            val read = input.read(buf)
            if (read > 0) buf.copyOf(read) else null
        }
    }.getOrNull()
    return detectWallpaperMediaType(filePath, headerBytes = header)
}

/**
 * Detects animated WebP containers per RFC 9649 (RIFF/WEBP + VP8X animation flag or ANIM chunk).
 */
internal fun isAnimatedWebpHeader(bytes: ByteArray?): Boolean {
    if (bytes == null || bytes.size < 16) return false
    val isRiff = bytes[0] == 'R'.code.toByte() &&
        bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte() &&
        bytes[3] == 'F'.code.toByte()
    val isWebp = bytes[8] == 'W'.code.toByte() &&
        bytes[9] == 'E'.code.toByte() &&
        bytes[10] == 'B'.code.toByte() &&
        bytes[11] == 'P'.code.toByte()
    if (!isRiff || !isWebp) return false

    // Check VP8X extended header animation bit (bit 1 = 0x02 at offset 20)
    if (bytes.size >= 21 &&
        bytes[12] == 'V'.code.toByte() &&
        bytes[13] == 'P'.code.toByte() &&
        bytes[14] == '8'.code.toByte() &&
        bytes[15] == 'X'.code.toByte()
    ) {
        if ((bytes[20].toInt() and 0x02) != 0) return true
    }

    // Fallback scan for "ANIM" fourCC in header buffer
    for (i in 12..bytes.size - 4) {
        if (bytes[i] == 'A'.code.toByte() &&
            bytes[i + 1] == 'N'.code.toByte() &&
            bytes[i + 2] == 'I'.code.toByte() &&
            bytes[i + 3] == 'M'.code.toByte()
        ) {
            return true
        }
    }
    return false
}

/**
 * Smart pause predicate for live wallpapers on Android 9 head units.
 *
 * Uses [isActivityStarted] (onStart/onStop) rather than onResume so that live wallpapers still play
 * reliably on Android 9 in all Home button modes — including off-screen freeform hiding
 * (`dclwm minimize` to 2000,2000), fullscreen windowing-mode switching, and moveTaskToBack,
 * where a background freeform stack may keep HOME in STARTED instead of RESUMED.
 *
 * Returns `true` whenever a foreign app, settings screen/overlay, top shade, or camera (AVM/Camd)
 * is active, or the home activity is stopped / not yet drawn — releasing hardware video decoders
 * and stopping animation loops to keep 100% GPU/MediaCodec headroom free.
 */
internal fun shouldPauseLiveWallpaper(
    isFirstDrawComplete: Boolean,
    isActivityStarted: Boolean,
    isWindowFocused: Boolean,
    hiddenForExternalApp: Boolean,
    activeAppPackage: String?,
    isActiveAppFullscreen: Boolean,
    isTopShadeOpen: Boolean,
    isVehicleSettingsOpen: Boolean,
    isVehicleSettingsOverlayShowing: Boolean,
    isVehicleSettingsActivityOpen: Boolean = false,
    isAboutOverlayShowing: Boolean,
    isQuickSettingsOpen: Boolean,
    isAvmActive: Boolean,
    isCamdCameraActive: Boolean,
): Boolean = !shouldAnimateRightPanel(
    isFirstDrawComplete = isFirstDrawComplete,
    isActivityStarted = isActivityStarted,
    isWindowFocused = isWindowFocused,
    hiddenForExternalApp = hiddenForExternalApp,
    activeAppPackage = activeAppPackage,
    isActiveAppFullscreen = isActiveAppFullscreen,
    isTopShadeOpen = isTopShadeOpen,
    isVehicleSettingsOpen = isVehicleSettingsOpen,
    isVehicleSettingsOverlayShowing = isVehicleSettingsOverlayShowing,
    isVehicleSettingsActivityOpen = isVehicleSettingsActivityOpen,
    isAboutOverlayShowing = isAboutOverlayShowing,
    isQuickSettingsOpen = isQuickSettingsOpen,
    isAvmActive = isAvmActive,
    isCamdCameraActive = isCamdCameraActive,
)

/**
 * Computes TextureView post-stretch scale factors around the view center to match [WallpaperScaleMode].
 * Note: TextureView's identity transform stretches the video to fill (viewWidth x viewHeight).
 */
internal fun computeVideoTransformScale(
    viewWidth: Int,
    viewHeight: Int,
    videoWidth: Int,
    videoHeight: Int,
    scaleMode: WallpaperScaleMode,
): VideoTransformScale {
    if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
        return VideoTransformScale(1f, 1f)
    }
    val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
    val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
    return when (scaleMode) {
        WallpaperScaleMode.FILL_BOUNDS -> VideoTransformScale(1f, 1f)
        WallpaperScaleMode.FILL_HEIGHT -> VideoTransformScale(
            scaleX = videoAspect / viewAspect,
            scaleY = 1f,
        )
        WallpaperScaleMode.FILL_WIDTH -> VideoTransformScale(
            scaleX = 1f,
            scaleY = viewAspect / videoAspect,
        )
    }
}

/**
 * Computes ImageView [ImageView.ScaleType.MATRIX] transform parameters to match [WallpaperScaleMode].
 */
internal fun computeDrawableMatrixParams(
    viewWidth: Int,
    viewHeight: Int,
    drawableWidth: Int,
    drawableHeight: Int,
    scaleMode: WallpaperScaleMode,
): DrawableMatrixParams {
    if (viewWidth <= 0 || viewHeight <= 0 || drawableWidth <= 0 || drawableHeight <= 0) {
        return DrawableMatrixParams(1f, 1f, 0f, 0f)
    }
    return when (scaleMode) {
        WallpaperScaleMode.FILL_BOUNDS -> DrawableMatrixParams(
            scaleX = viewWidth.toFloat() / drawableWidth.toFloat(),
            scaleY = viewHeight.toFloat() / drawableHeight.toFloat(),
            translateX = 0f,
            translateY = 0f,
        )
        WallpaperScaleMode.FILL_HEIGHT -> {
            val scale = viewHeight.toFloat() / drawableHeight.toFloat()
            val scaledW = drawableWidth * scale
            DrawableMatrixParams(
                scaleX = scale,
                scaleY = scale,
                translateX = (viewWidth - scaledW) / 2f,
                translateY = 0f,
            )
        }
        WallpaperScaleMode.FILL_WIDTH -> {
            val scale = viewWidth.toFloat() / drawableWidth.toFloat()
            val scaledH = drawableHeight * scale
            DrawableMatrixParams(
                scaleX = scale,
                scaleY = scale,
                translateX = 0f,
                translateY = (viewHeight - scaledH) / 2f,
            )
        }
    }
}

internal object LiveWallpaperPosterCache {
    private const val MAX_ENTRIES = 4
    private val cache = object : LinkedHashMap<String, ImageBitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    private fun keyFor(file: File): String = "${file.absolutePath}|${file.lastModified()}|${file.length()}"

    @Synchronized
    fun get(file: File): ImageBitmap? {
        if (!file.isFile) return null
        return cache[keyFor(file)]
    }

    @Synchronized
    fun put(file: File, bitmap: ImageBitmap) {
        cache[keyFor(file)] = bitmap
    }

    @Synchronized
    fun invalidatePath(path: String) {
        if (path.isBlank()) return
        val prefix = "$path|"
        val it = cache.entries.iterator()
        while (it.hasNext()) {
            if (it.next().key.startsWith(prefix)) {
                it.remove()
            }
        }
        WallpaperAmbilightCache.invalidatePath(path)
    }
}

internal fun extractVideoPosterBitmap(file: File): ImageBitmap? {
    if (!file.isFile || file.length() <= 0L) return null
    LiveWallpaperPosterCache.get(file)?.let { return it }

    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val rawFrame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.frameAtTime
            ?: return null

        val maxDim = WALLPAPER_MAX_DIM_PX
        val finalBitmap = if (rawFrame.width > maxDim || rawFrame.height > maxDim) {
            val scale = minOf(maxDim.toFloat() / rawFrame.width, maxDim.toFloat() / rawFrame.height)
            val targetW = (rawFrame.width * scale).toInt().coerceAtLeast(1)
            val targetH = (rawFrame.height * scale).toInt().coerceAtLeast(1)
            val scaled = rawFrame.scale(targetW, targetH)
            if (scaled !== rawFrame) {
                rawFrame.recycle()
            }
            scaled
        } else {
            rawFrame
        }
        val imageBitmap = finalBitmap.toOwnedImageBitmap() ?: return null
        LiveWallpaperPosterCache.put(file, imageBitmap)
        imageBitmap
    } catch (e: Exception) {
        Log.w(TAG, "Failed to extract video poster frame for ${file.name}", e)
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private val videoReleaseExecutor = Executors.newSingleThreadExecutor { runnable ->
    Thread(
        {
            LauncherCpuAffinity.applyOptionalServiceBackgroundAffinityOnCurrentThread("LiveWallpaperRelease")
            runnable.run()
        },
        "LiveWallpaperRelease",
    ).apply { isDaemon = true }
}

@Volatile
private var pendingVideoReleaseFuture: java.util.concurrent.Future<*>? = null

/**
 * Manages [MediaPlayer] bound to a [TextureView] with instant hardware decoder release on pause.
 *
 * When paused, the Surface is detached synchronously on the main thread (preventing BufferQueue
 * conflicts on rapid resume) and [MediaPlayer.release] runs off the main thread so AVM/settings
 * transitions stay jank-free.
 */
private class LiveVideoWallpaperController(
    private val textureView: TextureView,
    private val onFirstFrameRendered: (Boolean) -> Unit,
    private val onPlaybackError: () -> Unit,
) : TextureView.SurfaceTextureListener {

    private var filePath: String = ""
    private var wallpaperRevision: Int = 0
    private var scaleMode: WallpaperScaleMode = WallpaperScaleMode.FILL_HEIGHT
    private var paused: Boolean = true
    private var destroyed: Boolean = false

    private var surfaceTexture: SurfaceTexture? = null
    private var activeSurface: Surface? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared: Boolean = false
    private var hasReportedFirstFrame: Boolean = false

    private var lastPositionMs: Int = 0
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    private var viewWidth: Int = 0
    private var viewHeight: Int = 0

    init {
        textureView.surfaceTextureListener = this
        if (textureView.isAvailable) {
            surfaceTexture = textureView.surfaceTexture
            viewWidth = textureView.width
            viewHeight = textureView.height
        }
    }

    fun update(
        newFilePath: String,
        newWallpaperRevision: Int,
        newScaleMode: WallpaperScaleMode,
        newPaused: Boolean,
    ) {
        if (destroyed) return
        val fileChanged = newFilePath != filePath || newWallpaperRevision != wallpaperRevision
        val scaleChanged = newScaleMode != scaleMode
        val pauseChanged = newPaused != paused

        if (fileChanged) {
            releasePlayer(savePosition = false)
            filePath = newFilePath
            wallpaperRevision = newWallpaperRevision
            lastPositionMs = 0
            videoWidth = 0
            videoHeight = 0
        }
        if (scaleChanged) {
            scaleMode = newScaleMode
            applyTransform()
        }
        paused = newPaused

        if (paused) {
            if (mediaPlayer != null) {
                if (pauseChanged) {
                    Log.d(TAG, "Smart pause -> releasing MediaPlayer at pos=${lastPositionMs}ms")
                }
                releasePlayer(savePosition = true)
            }
        } else {
            if (mediaPlayer == null && surfaceTexture != null && filePath.isNotBlank()) {
                startPlayer()
            }
        }
    }

    fun currentPlaybackPositionMs(): Long {
        val mp = mediaPlayer ?: return lastPositionMs.toLong()
        if (!isPrepared) return lastPositionMs.toLong()
        return runCatching { mp.currentPosition.toLong() }.getOrDefault(lastPositionMs.toLong())
    }

    fun currentVideoDimensions(): Pair<Int, Int> = videoWidth to videoHeight

    fun destroy() {
        destroyed = true
        textureView.surfaceTextureListener = null
        releasePlayer(savePosition = false)
        surfaceTexture = null
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        surfaceTexture = surface
        viewWidth = width
        viewHeight = height
        applyTransform()
        if (!destroyed && !paused && mediaPlayer == null && filePath.isNotBlank()) {
            startPlayer()
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        applyTransform()
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        releasePlayer(savePosition = true)
        if (surfaceTexture === surface) {
            surfaceTexture = null
        }
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        if (!hasReportedFirstFrame && isPrepared && !paused && mediaPlayer?.isPlaying == true) {
            hasReportedFirstFrame = true
            onFirstFrameRendered(true)
        }
    }

    private fun startPlayer() {
        val st = surfaceTexture ?: return
        val file = File(filePath)
        if (!file.isFile || file.length() <= 0L) {
            onPlaybackError()
            return
        }

        releasePlayer(savePosition = false)
        // Briefly await pending async MediaPlayer release so hardware MediaCodec slots
        // are freed before instantiating a new decoder on constrained Android 9 head units.
        pendingVideoReleaseFuture?.let { future ->
            runCatching { future.get(250L, java.util.concurrent.TimeUnit.MILLISECONDS) }
            pendingVideoReleaseFuture = null
        }

        val surface = Surface(st)
        val mp = MediaPlayer()
        activeSurface = surface
        mediaPlayer = mp
        isPrepared = false
        hasReportedFirstFrame = false

        try {
            mp.setSurface(surface)
            mp.setDataSource(file.absolutePath)
            mp.isLooping = true
            mp.setVolume(0f, 0f)

            mp.setOnVideoSizeChangedListener { player, width, height ->
                if (mediaPlayer !== player || destroyed) return@setOnVideoSizeChangedListener
                if (width > 0 && height > 0) {
                    videoWidth = width
                    videoHeight = height
                    applyTransform()
                }
            }

            mp.setOnInfoListener { player, what, _ ->
                if (mediaPlayer !== player || destroyed || paused) return@setOnInfoListener false
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    if (!hasReportedFirstFrame) {
                        hasReportedFirstFrame = true
                        onFirstFrameRendered(true)
                    }
                }
                false
            }

            mp.setOnCompletionListener { player ->
                if (mediaPlayer !== player || destroyed || paused) return@setOnCompletionListener
                runCatching {
                    player.seekTo(0)
                    player.start()
                }
            }

            mp.setOnErrorListener { player, what, extra ->
                if (mediaPlayer !== player) return@setOnErrorListener true
                Log.e(TAG, "MediaPlayer error what=$what extra=$extra file=${file.name}")
                releasePlayer(savePosition = false)
                onPlaybackError()
                true
            }

            mp.setOnPreparedListener { player ->
                if (mediaPlayer !== player || destroyed || paused) {
                    releasePlayer(savePosition = false)
                    return@setOnPreparedListener
                }
                isPrepared = true
                if (player.videoWidth > 0 && player.videoHeight > 0) {
                    videoWidth = player.videoWidth
                    videoHeight = player.videoHeight
                    applyTransform()
                }
                player.setVolume(0f, 0f)
                player.isLooping = true
                val duration = runCatching { player.duration }.getOrDefault(0)
                if (lastPositionMs > 0 && duration > 500 && lastPositionMs < duration - 200) {
                    runCatching {
                        player.seekTo(lastPositionMs.toLong(), MediaPlayer.SEEK_CLOSEST_SYNC)
                    }
                }
                runCatching {
                    player.start()
                }.onFailure { e ->
                    Log.e(TAG, "MediaPlayer start failed", e)
                    releasePlayer(savePosition = false)
                    onPlaybackError()
                }
            }

            mp.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPlayer for ${file.name}", e)
            releasePlayer(savePosition = false)
            onPlaybackError()
        }
    }

    private fun releasePlayer(savePosition: Boolean) {
        val mp = mediaPlayer
        val surface = activeSurface
        val wasPrepared = isPrepared

        mediaPlayer = null
        activeSurface = null
        isPrepared = false
        if (hasReportedFirstFrame) {
            hasReportedFirstFrame = false
            onFirstFrameRendered(false)
        }

        if (mp != null) {
            if (savePosition && wasPrepared) {
                lastPositionMs = runCatching { mp.currentPosition }.getOrDefault(lastPositionMs)
            }
            mp.setOnPreparedListener(null)
            mp.setOnVideoSizeChangedListener(null)
            mp.setOnInfoListener(null)
            mp.setOnCompletionListener(null)
            mp.setOnErrorListener(null)

            // Detach Surface synchronously on main thread to avoid BufferQueue races on fast resume
            runCatching { if (wasPrepared && mp.isPlaying) mp.pause() }
            runCatching { mp.setSurface(null) }
            runCatching { surface?.release() }

            pendingVideoReleaseFuture = videoReleaseExecutor.submit {
                runCatching { mp.reset() }
                runCatching { mp.release() }
            }
        } else {
            runCatching { surface?.release() }
        }
    }

    private fun applyTransform() {
        val w = viewWidth.takeIf { it > 0 } ?: textureView.width
        val h = viewHeight.takeIf { it > 0 } ?: textureView.height
        if (w <= 0 || h <= 0 || videoWidth <= 0 || videoHeight <= 0) return

        val scale = computeVideoTransformScale(
            viewWidth = w,
            viewHeight = h,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            scaleMode = scaleMode,
        )
        val matrix = Matrix().apply {
            setScale(scale.scaleX, scale.scaleY, w / 2f, h / 2f)
        }
        textureView.setTransform(matrix)
    }
}

private class ScaledDrawableImageView(context: Context) : ImageView(context) {
    private var currentScaleMode: WallpaperScaleMode = WallpaperScaleMode.FILL_HEIGHT

    init {
        scaleType = ScaleType.MATRIX
    }

    fun setWallpaperScaleMode(mode: WallpaperScaleMode) {
        if (currentScaleMode != mode) {
            currentScaleMode = mode
            updateImageMatrix()
        }
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        updateImageMatrix()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateImageMatrix()
    }

    private fun updateImageMatrix() {
        val d = drawable ?: return
        val w = width
        val h = height
        val dw = d.intrinsicWidth
        val dh = d.intrinsicHeight
        if (w <= 0 || h <= 0 || dw <= 0 || dh <= 0) return

        val params = computeDrawableMatrixParams(
            viewWidth = w,
            viewHeight = h,
            drawableWidth = dw,
            drawableHeight = dh,
            scaleMode = currentScaleMode,
        )
        imageMatrix = Matrix().apply {
            setScale(params.scaleX, params.scaleY)
            postTranslate(params.translateX, params.translateY)
        }
    }
}

private fun decodeAnimatedWallpaperDrawable(file: File): Drawable? {
    if (!file.isFile || file.length() <= 0L) return null
    return runCatching {
        val source = ImageDecoder.createSource(file)
        ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            if (w > WALLPAPER_MAX_DIM_PX || h > WALLPAPER_MAX_DIM_PX) {
                val scale = minOf(WALLPAPER_MAX_DIM_PX.toFloat() / w, WALLPAPER_MAX_DIM_PX.toFloat() / h)
                decoder.setTargetSize(
                    (w * scale).toInt().coerceAtLeast(1),
                    (h * scale).toInt().coerceAtLeast(1),
                )
            }
        }
    }.onFailure { e ->
        Log.e(TAG, "Failed to decode animated wallpaper ${file.name}", e)
    }.getOrNull()
}

private fun WallpaperScaleMode.toComposeContentScale(): ContentScale = when (this) {
    WallpaperScaleMode.FILL_HEIGHT -> ContentScale.FillHeight
    WallpaperScaleMode.FILL_WIDTH -> ContentScale.FillWidth
    WallpaperScaleMode.FILL_BOUNDS -> ContentScale.FillBounds
}

/**
 * Renders the right-panel desktop wallpaper supporting static images, animated GIF/WebP,
 * and looping muted video (MP4/WebM) with smart pause/release when covered by apps,
 * settings, top shade, or AVM/Camd cameras.
 */
@Composable
internal fun LauncherDesktopWallpaperLayer(
    activeWallpaperUri: String,
    wallpaperRevision: Int,
    isDarkTheme: Boolean,
    scaleMode: WallpaperScaleMode,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val syncMediaType = remember(activeWallpaperUri) {
        if (activeWallpaperUri.substringAfterLast('.', "").lowercase() == "webp") {
            null
        } else {
            detectWallpaperMediaType(activeWallpaperUri)
        }
    }
    val detectedWebpType by produceState(
        initialValue = WallpaperMediaType.STATIC_IMAGE,
        key1 = activeWallpaperUri,
        key2 = wallpaperRevision,
    ) {
        if (syncMediaType == null) {
            value = withContext(Dispatchers.IO) {
                detectWallpaperMediaTypeFromPath(activeWallpaperUri)
            }
        }
    }
    val mediaType = syncMediaType ?: detectedWebpType

    if (mediaType == WallpaperMediaType.STATIC_IMAGE) {
        LaunchedEffect(activeWallpaperUri, wallpaperRevision, isDarkTheme) {
            LiveWallpaperAmbilightBridge.clear()
        }
        val staticBitmap by produceState<ImageBitmap?>(
            initialValue = null,
            keys = arrayOf(context, activeWallpaperUri, isDarkTheme, wallpaperRevision),
        ) {
            value = withContext(Dispatchers.IO) {
                loadWallpaperImageBitmap(context, activeWallpaperUri, isDarkTheme)
            }
        }
        val bmp = staticBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = scaleMode.toComposeContentScale(),
                modifier = modifier,
            )
        }
        return
    }

    val animState = rememberRightPanelAnimationSnapshot()
    val shouldPause = !animState.shouldAnimate

    LaunchedEffect(shouldPause, mediaType) {
        Log.d(
            TAG,
            "Live wallpaper state: type=$mediaType paused=$shouldPause " +
                "(started=${animState.isActivityStarted}, app=${animState.activeAppPackage}, " +
                "shade=${animState.isTopShadeOpen}, " +
                "settings=${animState.isVehicleSettingsOpen || animState.isVehicleSettingsOverlayShowing || animState.isVehicleSettingsActivityOpen}, " +
                "avm=${animState.isAvmActive}, camd=${animState.isCamdCameraActive})",
        )
    }

    if (mediaType == WallpaperMediaType.VIDEO) {
        key(activeWallpaperUri, wallpaperRevision) {
            LiveVideoWallpaperLayer(
                filePath = activeWallpaperUri,
                wallpaperRevision = wallpaperRevision,
                isDarkTheme = isDarkTheme,
                scaleMode = scaleMode,
                paused = shouldPause,
                modifier = modifier,
            )
        }
    } else {
        AnimatedImageWallpaperLayer(
            filePath = activeWallpaperUri,
            wallpaperRevision = wallpaperRevision,
            isDarkTheme = isDarkTheme,
            scaleMode = scaleMode,
            paused = shouldPause,
            modifier = modifier,
        )
    }
}

private const val LIVE_AMBILIGHT_SAMPLE_INTERVAL_MS = 280L

@Composable
private fun LiveVideoWallpaperLayer(
    filePath: String,
    wallpaperRevision: Int,
    isDarkTheme: Boolean,
    scaleMode: WallpaperScaleMode,
    paused: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var playbackFailed by remember { mutableStateOf(false) }
    var isVideoFrameVisible by remember { mutableStateOf(false) }

    val posterBitmap by produceState<ImageBitmap?>(
        initialValue = remember(filePath, wallpaperRevision) { LiveWallpaperPosterCache.get(File(filePath)) },
        key1 = filePath,
        key2 = wallpaperRevision,
    ) {
        val file = File(filePath)
        val cached = LiveWallpaperPosterCache.get(file)
        if (cached != null) {
            value = cached
        } else {
            value = withContext(Dispatchers.IO) { extractVideoPosterBitmap(file) }
        }
    }

    val fallbackBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = context,
        key2 = isDarkTheme,
        key3 = posterBitmap == null,
    ) {
        value = if (posterBitmap == null) {
            withContext(Dispatchers.IO) { loadWallpaperImageBitmap(context, "", isDarkTheme) }
        } else {
            null
        }
    }

    val controllerHolder = remember { arrayOfNulls<LiveVideoWallpaperController>(1) }
    val textureViewHolder = remember { arrayOfNulls<TextureView>(1) }

    val ambilightRevision by LauncherAppConfigStore.wallpaperAmbilightRevisionFlow
        .collectAsStateWithLifecycle()
    val ambilightMode = remember(context, ambilightRevision) {
        LauncherAppConfigStore.wallpaperAmbilightMode(context)
    }
    val ambilightAnimateLive = remember(context, ambilightRevision) {
        LauncherAppConfigStore.wallpaperAmbilightAnimateLive(context)
    }
    val ambilightSourceKey = LiveWallpaperAmbilightBridge.buildSourceKey(
        uri = filePath,
        wallpaperRevision = wallpaperRevision,
        isDarkTheme = isDarkTheme,
        mode = ambilightMode,
    )

    DisposableEffect(ambilightSourceKey) {
        onDispose {
            LiveWallpaperAmbilightBridge.clear(ambilightSourceKey)
        }
    }

    val ambilightSamplingActive = ambilightMode != WallpaperAmbilightMode.OFF &&
        !paused &&
        isVideoFrameVisible &&
        !playbackFailed

    LaunchedEffect(
        ambilightSamplingActive,
        ambilightAnimateLive,
        filePath,
        wallpaperRevision,
        isDarkTheme,
        scaleMode,
        ambilightMode,
    ) {
        if (!ambilightSamplingActive) return@LaunchedEffect

        fun currentLeftPanelFraction(): Float {
            val leftW = LauncherEmbeddedBoundsState.leftPanelBounds?.width()?.toFloat() ?: 0f
            val totalW = LauncherEmbeddedBoundsState.contentRowBounds?.width()?.toFloat()
                ?: LauncherEmbeddedBoundsState.screenBounds?.width()?.toFloat()
                ?: 0f
            return if (leftW > 1f && totalW > 1f) (leftW / totalW).coerceIn(0f, 0.55f) else 0f
        }

        fun currentViewportAspect(): Float {
            val tv = textureViewHolder[0]
            if (tv != null && tv.width > 0 && tv.height > 0) {
                return tv.width.toFloat() / tv.height.toFloat().coerceAtLeast(1f)
            }
            val rightBounds = LauncherEmbeddedBoundsState.embeddedZoneBounds
            if (rightBounds != null && rightBounds.width() > 0 && rightBounds.height() > 0) {
                return rightBounds.width().toFloat() / rightBounds.height().toFloat().coerceAtLeast(1f)
            }
            return 16f / 9f
        }

        fun buildCacheKey(viewAspect: Float, leftFrac: Float): String = buildWallpaperAmbilightCacheKey(
            activeWallpaperUri = filePath,
            isDarkTheme = isDarkTheme,
            scaleMode = scaleMode,
            viewportAspect = viewAspect,
            leftPanelFraction = leftFrac,
            mode = ambilightMode,
        )

        // When live animation is OFF and the ~5th-frame snapshot is already cached, publish once
        // and exit immediately with zero getBitmap() calls and zero allocations.
        if (!ambilightAnimateLive) {
            val cachedKey = buildCacheKey(currentViewportAspect(), currentLeftPanelFraction())
            WallpaperAmbilightCache.get(cachedKey)?.let { cached ->
                LiveWallpaperAmbilightBridge.publish(ambilightSourceKey, cached)
                return@LaunchedEffect
            }
        }

        val reusableBitmap = Bitmap.createBitmap(
            AMBILIGHT_WORKING_WIDTH,
            AMBILIGHT_WORKING_HEIGHT,
            Bitmap.Config.ARGB_8888,
        )
        val rawPixels = IntArray(AMBILIGHT_WORKING_WIDTH * AMBILIGHT_WORKING_HEIGHT)
        var prevSmoothed: IntArray? = null
        var consecutiveEmptySamples = 0
        var fallbackTimeline: List<Pair<Long, WallpaperAmbilightData>>? = null
        var lastPublishedTimelineIdx = -1

        suspend fun sampleCurrentTextureFrame(
            tv: TextureView,
            ctrl: LiveVideoWallpaperController?,
            leftFrac: Float,
            prevCopy: IntArray?,
        ): Pair<IntArray, WallpaperAmbilightData>? {
            reusableBitmap.eraseColor(0)
            val captured = runCatching { tv.getBitmap(reusableBitmap) }.getOrNull() ?: return null
            captured.getPixels(
                rawPixels,
                0,
                AMBILIGHT_WORKING_WIDTH,
                0,
                0,
                AMBILIGHT_WORKING_WIDTH,
                AMBILIGHT_WORKING_HEIGHT,
            )
            if (!hasVisibleFrameContent(rawPixels)) return null
            val (vidW, vidH) = ctrl?.currentVideoDimensions() ?: (0 to 0)
            val viewAspect = tv.width.toFloat() / tv.height.toFloat().coerceAtLeast(1f)
            val snapshot = rawPixels.copyOf()
            return withContext(LauncherCpuAffinity.lightPollersDispatcher) {
                val croppedPixels = if (vidW > 0 && vidH > 0 && scaleMode != WallpaperScaleMode.FILL_BOUNDS) {
                    val videoAspect = vidW.toFloat() / vidH.toFloat()
                    val crop = if (scaleMode == WallpaperScaleMode.FILL_HEIGHT) {
                        if (videoAspect > viewAspect) {
                            val fracW = (viewAspect / videoAspect).coerceIn(0.1f, 1f)
                            val cw = (AMBILIGHT_WORKING_WIDTH * fracW).toInt().coerceIn(1, AMBILIGHT_WORKING_WIDTH)
                            WallpaperCropRect((AMBILIGHT_WORKING_WIDTH - cw) / 2, 0, cw, AMBILIGHT_WORKING_HEIGHT)
                        } else {
                            WallpaperCropRect(0, 0, AMBILIGHT_WORKING_WIDTH, AMBILIGHT_WORKING_HEIGHT)
                        }
                    } else {
                        if (videoAspect < viewAspect) {
                            val fracH = (videoAspect / viewAspect).coerceIn(0.1f, 1f)
                            val ch = (AMBILIGHT_WORKING_HEIGHT * fracH).toInt().coerceIn(1, AMBILIGHT_WORKING_HEIGHT)
                            WallpaperCropRect(0, (AMBILIGHT_WORKING_HEIGHT - ch) / 2, AMBILIGHT_WORKING_WIDTH, ch)
                        } else {
                            WallpaperCropRect(0, 0, AMBILIGHT_WORKING_WIDTH, AMBILIGHT_WORKING_HEIGHT)
                        }
                    }
                    cropAndResizePixels(
                        srcPixels = snapshot,
                        srcWidth = AMBILIGHT_WORKING_WIDTH,
                        srcHeight = AMBILIGHT_WORKING_HEIGHT,
                        crop = crop,
                    )
                } else {
                    snapshot
                }
                val framePixels = if (ambilightMode == WallpaperAmbilightMode.BLURRED_WALLPAPER) {
                    blendPixelBuffers(prevCopy, croppedPixels, blendFactor = 0.55f)
                } else {
                    croppedPixels
                }
                val data = computeWallpaperAmbilightDataFromPixels(
                    pixels = framePixels,
                    width = AMBILIGHT_WORKING_WIDTH,
                    height = AMBILIGHT_WORKING_HEIGHT,
                    isDarkTheme = isDarkTheme,
                    leftPanelFraction = leftFrac,
                    mode = ambilightMode,
                )
                framePixels to data
            }
        }

        try {
            if (!ambilightAnimateLive) {
                // Static ~5th-frame mode: wait ~180 ms (~5th frame at 25–30 FPS), capture ONE
                // visible 48x32 frame directly from TextureView (works for 4K/HEVC codecs where
                // MediaMetadataRetriever fails), cache it, publish once, and exit completely.
                delay(180L)
                var capturedOnce = false
                repeat(6) {
                    if (!isActive || capturedOnce) return@repeat
                    val tv = textureViewHolder[0]
                    val ctrl = controllerHolder[0]
                    val leftFrac = currentLeftPanelFraction()
                    if (tv != null && tv.isAvailable && tv.width > 0 && tv.height > 0) {
                        val viewAspect = tv.width.toFloat() / tv.height.toFloat().coerceAtLeast(1f)
                        val sampled = sampleCurrentTextureFrame(tv, ctrl, leftFrac, prevCopy = null)
                        if (sampled != null) {
                            val cacheKey = buildCacheKey(viewAspect, leftFrac)
                            WallpaperAmbilightCache.put(cacheKey, sampled.second)
                            LiveWallpaperAmbilightBridge.publish(ambilightSourceKey, sampled.second)
                            capturedOnce = true
                            return@repeat
                        }
                    }
                    delay(120L)
                }
                if (!capturedOnce && isActive) {
                    val viewAspect = currentViewportAspect()
                    val leftFrac = currentLeftPanelFraction()
                    val fallbackData = withContext(LauncherCpuAffinity.lightPollersDispatcher) {
                        val frame = extractSmallVideoFrame(File(filePath)) ?: return@withContext null
                        try {
                            val (cLeft, cTop, cWidth, cHeight) = computeVisibleWallpaperCropRect(
                                bitmapWidth = frame.width,
                                bitmapHeight = frame.height,
                                viewportAspect = viewAspect,
                                scaleMode = scaleMode,
                            )
                            val cropped = Bitmap.createBitmap(frame, cLeft, cTop, cWidth, cHeight)
                            val working = Bitmap.createScaledBitmap(cropped, AMBILIGHT_WORKING_WIDTH, AMBILIGHT_WORKING_HEIGHT, true)
                            if (cropped !== frame && cropped !== working) cropped.recycle()
                            val px = IntArray(AMBILIGHT_WORKING_WIDTH * AMBILIGHT_WORKING_HEIGHT)
                            working.getPixels(px, 0, AMBILIGHT_WORKING_WIDTH, 0, 0, AMBILIGHT_WORKING_WIDTH, AMBILIGHT_WORKING_HEIGHT)
                            if (working !== frame) working.recycle()
                            computeWallpaperAmbilightDataFromPixels(
                                pixels = px,
                                width = AMBILIGHT_WORKING_WIDTH,
                                height = AMBILIGHT_WORKING_HEIGHT,
                                isDarkTheme = isDarkTheme,
                                leftPanelFraction = leftFrac,
                                mode = ambilightMode,
                            )
                        } finally {
                            if (!frame.isRecycled) frame.recycle()
                        }
                    }
                    if (fallbackData != null) {
                        WallpaperAmbilightCache.put(buildCacheKey(viewAspect, leftFrac), fallbackData)
                        LiveWallpaperAmbilightBridge.publish(ambilightSourceKey, fallbackData)
                    }
                }
                return@LaunchedEffect
            }

            delay(140L)
            while (isActive) {
                val tv = textureViewHolder[0]
                val ctrl = controllerHolder[0]
                val leftFrac = currentLeftPanelFraction()
                if (tv != null && tv.isAvailable && tv.width > 0 && tv.height > 0 && fallbackTimeline == null) {
                    val sampled = sampleCurrentTextureFrame(tv, ctrl, leftFrac, prevSmoothed)
                    if (sampled != null) {
                        consecutiveEmptySamples = 0
                        prevSmoothed = sampled.first
                        val viewAspect = tv.width.toFloat() / tv.height.toFloat().coerceAtLeast(1f)
                        val cacheKey = buildCacheKey(viewAspect, leftFrac)
                        if (WallpaperAmbilightCache.get(cacheKey) == null) {
                            WallpaperAmbilightCache.put(cacheKey, sampled.second)
                        }
                        LiveWallpaperAmbilightBridge.publish(ambilightSourceKey, sampled.second)
                    } else {
                        consecutiveEmptySamples++
                        if (consecutiveEmptySamples >= 3) {
                            val viewAspect = tv.width.toFloat() / tv.height.toFloat().coerceAtLeast(1f)
                            fallbackTimeline = withContext(LauncherCpuAffinity.lightPollersDispatcher) {
                                extractVideoAmbilightTimeline(
                                    file = File(filePath),
                                    isDarkTheme = isDarkTheme,
                                    scaleMode = scaleMode,
                                    viewportAspect = viewAspect,
                                    leftPanelFraction = leftFrac,
                                    mode = ambilightMode,
                                )
                            }
                        }
                    }
                }

                val timeline = fallbackTimeline
                if (timeline != null && timeline.isNotEmpty()) {
                    val posMs = ctrl?.currentPlaybackPositionMs() ?: 0L
                    var idx = 0
                    for (i in timeline.indices) {
                        if (timeline[i].first <= posMs) idx = i else break
                    }
                    if (idx != lastPublishedTimelineIdx) {
                        lastPublishedTimelineIdx = idx
                        LiveWallpaperAmbilightBridge.publish(ambilightSourceKey, timeline[idx].second)
                    }
                }
                delay(LIVE_AMBILIGHT_SAMPLE_INTERVAL_MS)
            }
        } finally {
            if (!reusableBitmap.isRecycled) {
                reusableBitmap.recycle()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            controllerHolder[0]?.destroy()
            controllerHolder[0] = null
            textureViewHolder[0] = null
        }
    }

    Box(modifier = modifier.clipToBounds()) {
        val backingBitmap = posterBitmap ?: fallbackBitmap
        if (backingBitmap != null) {
            Image(
                bitmap = backingBitmap,
                contentDescription = null,
                contentScale = scaleMode.toComposeContentScale(),
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (!playbackFailed) {
            AndroidView(
                factory = { ctx ->
                    controllerHolder[0]?.destroy()
                    TextureView(ctx).apply {
                        isOpaque = false
                        textureViewHolder[0] = this
                        val ctrl = LiveVideoWallpaperController(
                            textureView = this,
                            onFirstFrameRendered = { visible -> isVideoFrameVisible = visible },
                            onPlaybackError = {
                                isVideoFrameVisible = false
                                playbackFailed = true
                            },
                        )
                        controllerHolder[0] = ctrl
                        ctrl.update(filePath, wallpaperRevision, scaleMode, paused)
                    }
                },
                update = {
                    controllerHolder[0]?.update(filePath, wallpaperRevision, scaleMode, paused)
                },
                onRelease = {
                    controllerHolder[0]?.destroy()
                    controllerHolder[0] = null
                    textureViewHolder[0] = null
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (isVideoFrameVisible) 1f else 0f },
            )
        }
    }
}

@Composable
private fun AnimatedImageWallpaperLayer(
    filePath: String,
    wallpaperRevision: Int,
    isDarkTheme: Boolean,
    scaleMode: WallpaperScaleMode,
    paused: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val decodedDrawable by produceState<Drawable?>(
        initialValue = null,
        key1 = filePath,
        key2 = wallpaperRevision,
    ) {
        value = withContext(Dispatchers.IO) {
            decodeAnimatedWallpaperDrawable(File(filePath))
        }
    }

    DisposableEffect(decodedDrawable) {
        onDispose {
            (decodedDrawable as? AnimatedImageDrawable)?.stop()
        }
    }

    LaunchedEffect(decodedDrawable, paused) {
        val animated = decodedDrawable as? AnimatedImageDrawable ?: return@LaunchedEffect
        animated.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        if (paused) {
            if (animated.isRunning) animated.stop()
        } else {
            if (!animated.isRunning) animated.start()
        }
    }

    val ambilightRevision by LauncherAppConfigStore.wallpaperAmbilightRevisionFlow
        .collectAsStateWithLifecycle()
    val ambilightMode = remember(context, ambilightRevision) {
        LauncherAppConfigStore.wallpaperAmbilightMode(context)
    }
    val ambilightAnimateLive = remember(context, ambilightRevision) {
        LauncherAppConfigStore.wallpaperAmbilightAnimateLive(context)
    }
    val ambilightSourceKey = LiveWallpaperAmbilightBridge.buildSourceKey(
        uri = filePath,
        wallpaperRevision = wallpaperRevision,
        isDarkTheme = isDarkTheme,
        mode = ambilightMode,
    )

    DisposableEffect(ambilightSourceKey) {
        onDispose {
            LiveWallpaperAmbilightBridge.clear(ambilightSourceKey)
        }
    }

    val ambilightAnimSamplingActive = ambilightMode != WallpaperAmbilightMode.OFF &&
        !paused &&
        decodedDrawable != null

    LaunchedEffect(
        ambilightAnimSamplingActive,
        ambilightAnimateLive,
        decodedDrawable,
        filePath,
        wallpaperRevision,
        isDarkTheme,
        scaleMode,
        ambilightMode,
    ) {
        val currentDrawable = decodedDrawable ?: return@LaunchedEffect
        if (!ambilightAnimSamplingActive) return@LaunchedEffect

        fun currentLeftPanelFraction(): Float {
            val leftW = LauncherEmbeddedBoundsState.leftPanelBounds?.width()?.toFloat() ?: 0f
            val totalW = LauncherEmbeddedBoundsState.contentRowBounds?.width()?.toFloat()
                ?: LauncherEmbeddedBoundsState.screenBounds?.width()?.toFloat()
                ?: 0f
            return if (leftW > 1f && totalW > 1f) (leftW / totalW).coerceIn(0f, 0.55f) else 0f
        }

        fun currentViewportAspect(): Float {
            val rightBounds = LauncherEmbeddedBoundsState.embeddedZoneBounds
            return if (rightBounds != null && rightBounds.width() > 0 && rightBounds.height() > 0) {
                rightBounds.width().toFloat() / rightBounds.height().toFloat().coerceAtLeast(1f)
            } else {
                16f / 9f
            }
        }

        fun buildCacheKey(leftFrac: Float): String = buildWallpaperAmbilightCacheKey(
            activeWallpaperUri = filePath,
            isDarkTheme = isDarkTheme,
            scaleMode = scaleMode,
            viewportAspect = currentViewportAspect(),
            leftPanelFraction = leftFrac,
            mode = ambilightMode,
        )

        if (!ambilightAnimateLive) {
            val cachedKey = buildCacheKey(currentLeftPanelFraction())
            WallpaperAmbilightCache.get(cachedKey)?.let { cached ->
                LiveWallpaperAmbilightBridge.publish(ambilightSourceKey, cached)
                return@LaunchedEffect
            }
        }

        val reusableBitmap = Bitmap.createBitmap(
            AMBILIGHT_WORKING_WIDTH,
            AMBILIGHT_WORKING_HEIGHT,
            Bitmap.Config.ARGB_8888,
        )
        val drawCanvas = Canvas(reusableBitmap)
        val rawPixels = IntArray(AMBILIGHT_WORKING_WIDTH * AMBILIGHT_WORKING_HEIGHT)
        var prevSmoothed: IntArray? = null

        suspend fun sampleAnimatedFrame(
            leftFrac: Float,
            prevCopy: IntArray?,
        ): Pair<IntArray, WallpaperAmbilightData>? {
            val dw = currentDrawable.intrinsicWidth
            val dh = currentDrawable.intrinsicHeight
            if (dw <= 0 || dh <= 0) return null
            val savedBounds = currentDrawable.copyBounds()
            runCatching {
                val params = computeDrawableMatrixParams(
                    viewWidth = AMBILIGHT_WORKING_WIDTH,
                    viewHeight = AMBILIGHT_WORKING_HEIGHT,
                    drawableWidth = dw,
                    drawableHeight = dh,
                    scaleMode = scaleMode,
                )
                reusableBitmap.eraseColor(android.graphics.Color.BLACK)
                drawCanvas.save()
                drawCanvas.translate(params.translateX, params.translateY)
                drawCanvas.scale(params.scaleX, params.scaleY)
                currentDrawable.setBounds(0, 0, dw, dh)
                currentDrawable.draw(drawCanvas)
                drawCanvas.restore()
            }
            currentDrawable.bounds = savedBounds
            reusableBitmap.getPixels(
                rawPixels,
                0,
                AMBILIGHT_WORKING_WIDTH,
                0,
                0,
                AMBILIGHT_WORKING_WIDTH,
                AMBILIGHT_WORKING_HEIGHT,
            )
            if (!hasVisibleFrameContent(rawPixels)) return null
            val snapshot = rawPixels.copyOf()
            return withContext(LauncherCpuAffinity.lightPollersDispatcher) {
                val framePixels = if (ambilightMode == WallpaperAmbilightMode.BLURRED_WALLPAPER) {
                    blendPixelBuffers(prevCopy, snapshot, blendFactor = 0.55f)
                } else {
                    snapshot
                }
                val computed = computeWallpaperAmbilightDataFromPixels(
                    pixels = framePixels,
                    width = AMBILIGHT_WORKING_WIDTH,
                    height = AMBILIGHT_WORKING_HEIGHT,
                    isDarkTheme = isDarkTheme,
                    leftPanelFraction = leftFrac,
                    mode = ambilightMode,
                )
                framePixels to computed
            }
        }

        try {
            if (!ambilightAnimateLive) {
                delay(180L)
                var capturedOnce = false
                repeat(4) {
                    if (!isActive || capturedOnce) return@repeat
                    val leftFrac = currentLeftPanelFraction()
                    val sampled = sampleAnimatedFrame(leftFrac, prevCopy = null)
                    if (sampled != null) {
                        WallpaperAmbilightCache.put(buildCacheKey(leftFrac), sampled.second)
                        LiveWallpaperAmbilightBridge.publish(ambilightSourceKey, sampled.second)
                        capturedOnce = true
                        return@repeat
                    }
                    delay(120L)
                }
                return@LaunchedEffect
            }

            while (isActive) {
                val leftFrac = currentLeftPanelFraction()
                val sampled = sampleAnimatedFrame(leftFrac, prevSmoothed)
                if (sampled != null) {
                    prevSmoothed = sampled.first
                    val cacheKey = buildCacheKey(leftFrac)
                    if (WallpaperAmbilightCache.get(cacheKey) == null) {
                        WallpaperAmbilightCache.put(cacheKey, sampled.second)
                    }
                    LiveWallpaperAmbilightBridge.publish(ambilightSourceKey, sampled.second)
                }
                delay(LIVE_AMBILIGHT_SAMPLE_INTERVAL_MS)
            }
        } finally {
            if (!reusableBitmap.isRecycled) {
                reusableBitmap.recycle()
            }
        }
    }

    val drawable = decodedDrawable
    if (drawable == null) {
        val fallback by produceState<ImageBitmap?>(initialValue = null, key1 = context, key2 = isDarkTheme) {
            value = withContext(Dispatchers.IO) { loadWallpaperImageBitmap(context, "", isDarkTheme) }
        }
        val fb = fallback
        if (fb != null) {
            Image(
                bitmap = fb,
                contentDescription = null,
                contentScale = scaleMode.toComposeContentScale(),
                modifier = modifier,
            )
        }
        return
    }

    Box(modifier = modifier.clipToBounds()) {
        AndroidView(
            factory = { ctx ->
                ScaledDrawableImageView(ctx).apply {
                    setWallpaperScaleMode(scaleMode)
                    setImageDrawable(drawable)
                }
            },
            update = { view ->
                view.setWallpaperScaleMode(scaleMode)
                if (view.drawable !== drawable) {
                    view.setImageDrawable(drawable)
                }
            },
            onRelease = { view ->
                (view.drawable as? AnimatedImageDrawable)?.stop()
                view.setImageDrawable(null)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
