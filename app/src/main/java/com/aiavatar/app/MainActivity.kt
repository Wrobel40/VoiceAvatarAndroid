package com.aiavatar.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiavatar.app.ui.MainScreen
import com.aiavatar.app.ui.NavyDeep
import com.aiavatar.app.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.RECORD_AUDIO)

        if (needed.isNotEmpty()) requestPermissionLauncher.launch(needed.toTypedArray())

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = NavyDeep) {
                    val viewModel: MainViewModel = viewModel()
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}
