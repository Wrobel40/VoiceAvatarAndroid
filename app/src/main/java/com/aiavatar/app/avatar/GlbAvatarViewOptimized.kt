package com.aiavatar.app.avatar

import android.content.Context
import android.net.Uri
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aiavatar.app.audio.RecordingState
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

// Stałe dla optymalizacji
private const val MAX_FILE_SIZE_MB = 50L
private const val MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024

@Composable
fun GlbAvatarView(
    glbUri: Uri,
    recordingState: RecordingState,
    amplitude: Float,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var glbBytes by remember { mutableStateOf<ByteArray?>(null) }
    var loadProgress by remember { mutableStateOf(0f) }

    // Read bytes on IO thread z progressem i walidacją
    LaunchedEffect(glbUri) {
        isLoading = true
        loadError = null
        glbBytes = null
        loadProgress = 0f

        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val f = resolveUriToFile(context, glbUri)
                val fileSize = f.length()

                android.util.Log.d("GLB", "File size: ${fileSize / 1024}KB (${fileSize / (1024 * 1024)}MB)")

                // Walidacja rozmiaru
                if (fileSize > MAX_FILE_SIZE_BYTES) {
                    throw IllegalArgumentException(
                        "Plik za duży (${fileSize / (1024 * 1024)}MB). " +
                        "Maksymalny rozmiar: ${MAX_FILE_SIZE_MB}MB. " +
                        "Zoptymalizuj model w Blenderze (mniejsze tekstury, mniej polygonów)."
                    )
                }

                if (fileSize == 0L) {
                    throw IllegalArgumentException("Plik jest pusty lub uszkodzony")
                }

                // Dla dużych plików (>5MB) użyj memory-mapped buffer
                glbBytes = if (fileSize > 5 * 1024 * 1024) {
                    android.util.Log.d("GLB", "Using memory-mapped buffer for large file")
                    FileInputStream(f).use { fis ->
                        fis.channel.use { channel ->
                            val buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize)
                            val bytes = ByteArray(fileSize.toInt())
                            buffer.get(bytes)
                            bytes
                        }
                    }
                } else {
                    // Dla małych plików standardowe readBytes
                    f.readBytes()
                }

                loadProgress = 1f
                android.util.Log.d("GLB", "Loaded successfully: ${glbBytes?.size ?: 0} bytes")
            }
            isLoading = false
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("GLB", "Out of memory!", e)
            loadError = "Za mało pamięci! Model jest zbyt duży. " +
                       "Zrestartuj aplikację lub użyj mniejszego modelu."
            isLoading = false
        } catch (e: Exception) {
            android.util.Log.e("GLB", "Load error: ${e.message}", e)
            loadError = "Błąd: ${e.message}"
            isLoading = false
        }
    }

    val inf = rememberInfiniteTransition(label = "bob")
    val bobY by inf.animateFloat(-0.05f, 0.05f,
        infiniteRepeatable(tween(2400, easing = EaseInOutSine), RepeatMode.Reverse), "bob")

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            isLoading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    progress = { loadProgress },
                    color = Color(0xFF00D4FF),
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 4.dp
                )
                Spacer(Modifier.height(16.dp))
                Text("Ładowanie modelu 3D…", color = Color(0xFF00D4FF), fontSize = 14.sp)
                Text(
                    "Duży model sci-fi (do ${MAX_FILE_SIZE_MB}MB)",
                    color = Color(0xFF8899CC),
                    fontSize = 11.sp
                )
            }
            loadError != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text("⚠️", fontSize = 32.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Nie można załadować modelu",
                    color = Color(0xFFFF6644),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    loadError!!,
                    color = Color(0xFFAA7766),
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "💡 Wskazówka: Użyj modelu z mniejszymi teksturami (1K zamiast 2K)",
                    color = Color(0xFF6688AA),
                    fontSize = 10.sp
                )
            }
            glbBytes != null -> {
                ModelViewerSurfaceOptimized(
                    bytes = glbBytes!!,
                    bobY = bobY,
                    modifier = Modifier.fillMaxSize()
                )
                if (recordingState == RecordingState.LISTENING || amplitude > 0.15f) {
                    ListeningRingsOverlay(amplitude = amplitude, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun ModelViewerSurfaceOptimized(bytes: ByteArray, bobY: Float, modifier: Modifier) {
    val viewerState = remember { mutableStateOf<ModelViewer?>(null) }
    val errorState = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(bobY) {
        viewerState.value?.let { mv ->
            mv.asset?.let { asset ->
                try {
                    val tm = mv.engine.transformManager
                    val root = asset.root
                    if (tm.hasComponent(root)) {
                        val inst = tm.getInstance(root)
                        val m = FloatArray(16)
                        tm.getTransform(inst, m)
                        m[13] = bobY * 0.15f
                        tm.setTransform(inst, m)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    if (errorState.value != null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "Błąd renderera: ${errorState.value}",
                color = Color(0xFFFF6644),
                fontSize = 12.sp
            )
        }
        return
    }

    AndroidView(
        factory = { ctx ->
            try {
                Utils.init()

                SurfaceView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // USUNIĘTO: setZOrderOnTop(true) - powoduje crash na niektórych GPU
                    holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)

                    holder.addCallback(object : SurfaceHolder.Callback {
                        private var choreographer: Choreographer? = null
                        private var frameCallback: Choreographer.FrameCallback? = null
                        private var viewer: ModelViewer? = null
                        private var isDestroyed = false

                        override fun surfaceCreated(h: SurfaceHolder) {
                            if (isDestroyed) return

                            try {
                                android.util.Log.d("GLB", "Creating ModelViewer...")
                                viewer = ModelViewer(this@apply)
                                viewerState.value = viewer

                                // Load GLB z pełnym try-catch
                                try {
                                    android.util.Log.d("GLB", "Loading GLB (${bytes.size} bytes)...")
                                    viewer!!.loadModelGlb(ByteBuffer.wrap(bytes))
                                    viewer!!.transformToUnitCube()
                                    android.util.Log.d("GLB", "GLB loaded successfully")
                                } catch (e: Exception) {
                                    android.util.Log.e("GLB", "Failed to load GLB: ${e.message}", e)
                                    errorState.value = "Nieprawidłowy format GLB lub uszkodzony plik"
                                    return
                                }

                                startRenderLoop()
                            } catch (e: Exception) {
                                android.util.Log.e("GLB", "surfaceCreated failed: ${e.message}", e)
                                errorState.value = e.message ?: "Unknown error"
                            }
                        }

                        private fun startRenderLoop() {
                            choreographer = Choreographer.getInstance()
                            frameCallback = object : Choreographer.FrameCallback {
                                override fun doFrame(nanos: Long) {
                                    if (isDestroyed) return
                                    choreographer?.postFrameCallback(this)
                                    try {
                                        viewer?.render(nanos)
                                    } catch (e: Exception) {
                                        android.util.Log.e("GLB", "Render error: ${e.message}")
                                    }
                                }
                            }
                            choreographer?.postFrameCallback(frameCallback!!)
                        }

                        override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, h2: Int) {
                            try {
                                viewer?.let { v ->
                                    v.view.setResolution(w, h2)
                                }
                            } catch (_: Exception) {}
                        }

                        override fun surfaceDestroyed(h: SurfaceHolder) {
                            isDestroyed = true
                            frameCallback?.let { choreographer?.removeFrameCallback(it) }
                            frameCallback = null
                            choreographer = null
                            viewerState.value = null

                            // Bezpieczne niszczenie z opóźnieniem
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                try {
                                    viewer?.destroyModel()
                                    viewer?.destroy()
                                } catch (_: Exception) {}
                                viewer = null
                            }, 100)
                        }
                    })
                }
            } catch (e: Exception) {
                android.util.Log.e("GLB", "Factory error: ${e.message}", e)
                errorState.value = e.message
                // Zwróć pusty View zamiast crashować
                SurfaceView(ctx)
            }
        },
        modifier = modifier
    )
}

@Composable
fun ListeningRingsOverlay(amplitude: Float, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "lr")
    val pulse by inf.animateFloat(0.88f, 1f,
        infiniteRepeatable(tween(600, easing = EaseInOutSine), RepeatMode.Reverse), "lrp")
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val base = size.minDimension * 0.46f
        repeat(3) { i ->
            val r = (base + i * 18f + amplitude * 28f) * pulse
            drawCircle(
                color = Color(0xFF00D4FF).copy(alpha = (0.35f - i * 0.1f) * pulse),
                radius = r,
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
        }
    }
}

fun resolveUriToFile(context: Context, uri: Uri): File {
    if (uri.scheme == "file") return File(uri.path!!)

    val tmp = File(context.cacheDir, "avatar_glb_${System.currentTimeMillis()}.glb")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(tmp).use { output ->
            input.copyTo(output)
        }
    }
    return tmp
}
