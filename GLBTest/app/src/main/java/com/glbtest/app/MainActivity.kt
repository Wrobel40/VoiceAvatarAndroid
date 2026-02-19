package com.glbtest.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.filament.*
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {

    private lateinit var surfaceView: SurfaceView
    private lateinit var statusText: TextView
    private lateinit var loadButton: Button
    private var viewer: ModelViewer? = null
    private var choreographer: Choreographer? = null
    private var frameCallback: Choreographer.FrameCallback? = null

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { loadGlb(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simple UI
        val layout = FrameLayout(this)
        
        surfaceView = SurfaceView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        statusText = TextView(this).apply {
            text = "Kliknij przycisk aby wybrać plik GLB"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xAA000000.toInt())
            setPadding(20, 20, 20, 20)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 100
                leftMargin = 50
            }
        }
        
        loadButton = Button(this).apply {
            text = "📁 Wczytaj GLB"
            setOnClickListener { 
                getContent.launch("*/*") 
            }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 100
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            }
        }
        
        layout.addView(surfaceView)
        layout.addView(statusText)
        layout.addView(loadButton)
        setContentView(layout)

        // Check permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }

        // Init Filament
        Utils.init()
        setupSurface()
    }

    private fun setupSurface() {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.d("GLB-TEST", "Surface created")
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                Log.d("GLB-TEST", "Surface changed: ${width}x${height}")
                viewer?.let { 
                    it.view.viewport = Viewport(0, 0, width, height)
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                stopRenderLoop()
            }
        })
    }

    private fun loadGlb(uri: Uri) {
        statusText.text = "Ładowanie..."
        
        try {
            // Read file
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: run {
                statusText.text = "Błąd: Nie można odczytać pliku"
                return
            }
            
            statusText.text = "Załadowano: ${bytes.size / 1024}KB\nInicjalizacja Filament..."
            Log.d("GLB-TEST", "Loaded ${bytes.size} bytes")

            // Create ModelViewer
            if (viewer == null) {
                viewer = ModelViewer(surfaceView)
                Log.d("GLB-TEST", "ModelViewer created")
            }
            
            val viewer = this.viewer!!
            val engine = viewer.engine
            val scene = viewer.scene

            // Load model
            viewer.loadModelGlb(ByteBuffer.wrap(bytes))
            viewer.transformToUnitCube()
            
            statusText.text = "Model załadowany!\nEntities: ${viewer.asset?.entities?.size ?: 0}\nDodawanie światła..."
            Log.d("GLB-TEST", "Model loaded, entities: ${viewer.asset?.entities?.size}")

            // Add lights
            addLights(engine, scene)
            
            // Set camera
            viewer.camera?.let { cam ->
                cam.setExposure(16f, 1f / 125f)  // Manual exposure
                Log.d("GLB-TEST", "Camera exposure set")
            }

            statusText.text = "✅ Gotowe!\nModel: ${viewer.asset?.entities?.size ?: 0} entytów\nJeśli widzisz czarno - problem z materiałami/kamerą"
            
            startRenderLoop()
            
        } catch (e: Exception) {
            Log.e("GLB-TEST", "Error: ${e.message}", e)
            statusText.text = "❌ Błąd:\n${e.message}"
        }
    }

    private fun addLights(engine: Engine, scene: Scene) {
        // Light 1: Main directional
        val light1 = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 1.0f, 1.0f)
            .intensity(10_000_000f)
            .direction(0.0f, -1.0f, -1.0f)
            .castShadows(false)
            .build(engine, light1)
        scene.addEntity(light1)

        // Light 2: Fill
        val light2 = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(0.8f, 0.9f, 1.0f)
            .intensity(5_000_000f)
            .direction(-1.0f, -0.5f, 0.5f)
            .castShadows(false)
            .build(engine, light2)
        scene.addEntity(light2)

        // Light 3: Rim
        val light3 = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 0.8f, 0.6f)
            .intensity(3_000_000f)
            .direction(1.0f, 0.0f, 1.0f)
            .castShadows(false)
            .build(engine, light3)
        scene.addEntity(light3)

        // Ambient (IBL)
        val ibl = IndirectLight.Builder()
            .intensity(100_000f)
            .build(engine)
        scene.indirectLight = ibl

        // Skybox (gray)
        scene.skybox = Skybox.Builder()
            .color(0.3f, 0.3f, 0.35f, 1.0f)
            .build(engine)

        Log.d("GLB-TEST", "Lights added: 3 directional + IBL")
    }

    private fun startRenderLoop() {
        choreographer = Choreographer.getInstance()
        frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(nanos: Long) {
                choreographer?.postFrameCallback(this)
                try {
                    viewer?.render(nanos)
                } catch (e: Exception) {
                    Log.e("GLB-TEST", "Render error: ${e.message}")
                }
            }
        }
        choreographer?.postFrameCallback(frameCallback!!)
        Log.d("GLB-TEST", "Render loop started")
    }

    private fun stopRenderLoop() {
        frameCallback?.let { choreographer?.removeFrameCallback(it) }
        frameCallback = null
        choreographer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRenderLoop()
        try {
            viewer?.destroyModel()
        } catch (_: Exception) {}
    }
}
