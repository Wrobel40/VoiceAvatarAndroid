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
import java.io.FileOutputStream
import java.nio.ByteBuffer

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

    // Read bytes on IO thread
    LaunchedEffect(glbUri) {
        isLoading = true; loadError = null; glbBytes = null
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val f = resolveUriToFile(context, glbUri)
                android.util.Log.d("GLB", "Reading ${f.length() / 1024}KB")
                glbBytes = f.readBytes()
            }
            isLoading = false
        } catch (e: Exception) {
            loadError = "Błąd odczytu: ${e.message}"
            isLoading = false
        }
    }

    val inf = rememberInfiniteTransition(label = "bob")
    val bobY by inf.animateFloat(-0.05f, 0.05f,
        infiniteRepeatable(tween(2400, easing = EaseInOutSine), RepeatMode.Reverse), "bob")

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            isLoading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFF00D4FF), modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Ładowanie modelu 3D…", color = Color(0xFF8899CC), fontSize = 13.sp)
                Text("Proszę czekać…", color = Color(0xFF445566), fontSize = 11.sp)
            }
            loadError != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(20.dp)
            ) {
                Text("⚠️", fontSize = 28.sp)
                Spacer(Modifier.height(8.dp))
                Text("Błąd ładowania modelu", color = Color(0xFFFF6644), fontSize = 13.sp)
                Text(loadError!!, color = Color(0xFF885544), fontSize = 10.sp)
            }
            glbBytes != null -> {
                ModelViewerSurface(
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
private fun ModelViewerSurface(bytes: ByteArray, bobY: Float, modifier: Modifier) {
    // Keep ModelViewer reference stable across recompositions
    val viewerState = remember { mutableStateOf<ModelViewer?>(null) }

    // Update float animation on every recompose
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
                        // Only update Y translation (index 13 in column-major)
                        m[13] = bobY * 0.15f
                        tm.setTransform(inst, m)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            // Init Filament utils (idempotent — safe to call multiple times)
            Utils.init()

            SurfaceView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setZOrderOnTop(true)
                holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)

                holder.addCallback(object : SurfaceHolder.Callback {
                    private var choreographer: Choreographer? = null
                    private var frameCallback: Choreographer.FrameCallback? = null
                    private var viewer: ModelViewer? = null

                    override fun surfaceCreated(h: SurfaceHolder) {
                        try {
                            viewer = ModelViewer(this@apply)
                            viewerState.value = viewer

                            // Load GLB — bytes already read on IO thread
                            viewer!!.loadModelGlb(ByteBuffer.wrap(bytes))
                            viewer!!.transformToUnitCube()

                            startRenderLoop()
                        } catch (e: Exception) {
                            android.util.Log.e("GLB", "surfaceCreated failed: ${e.message}", e)
                        }
                    }

                    private fun startRenderLoop() {
                        choreographer = Choreographer.getInstance()
                        frameCallback = object : Choreographer.FrameCallback {
                            override fun doFrame(nanos: Long) {
                                choreographer?.postFrameCallback(this)
                                try { viewer?.render(nanos) } catch (_: Exception) {}
                            }
                        }
                        choreographer?.postFrameCallback(frameCallback!!)
                    }

                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, h2: Int) {}

                    override fun surfaceDestroyed(h: SurfaceHolder) {
                        frameCallback?.let { choreographer?.removeFrameCallback(it) }
                        viewerState.value = null
                        try { viewer?.destroyModel(); viewer = null } catch (_: Exception) {}
                    }
                })
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
        val cx = size.width / 2f; val cy = size.height / 2f; val base = size.minDimension * 0.46f
        repeat(3) { i ->
            val r = (base + i * 18f + amplitude * 28f) * pulse
            drawCircle(color = Color(0xFF00D4FF).copy(alpha = (0.35f - i * 0.1f) * pulse),
                radius = r, center = androidx.compose.ui.geometry.Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
        }
    }
}

fun resolveUriToFile(context: Context, uri: Uri): File {
    if (uri.scheme == "file") return File(uri.path!!)
    val tmp = File(context.cacheDir, "avatar_glb.glb")
    context.contentResolver.openInputStream(uri)?.use { i ->
        FileOutputStream(tmp).use { o -> i.copyTo(o) }
    }
    return tmp
}
