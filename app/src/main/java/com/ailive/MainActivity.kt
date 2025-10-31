package com.ailive

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ailive.ai.models.ModelManager
import com.ailive.audio.AudioManager
import com.ailive.audio.CommandRouter
import com.ailive.audio.SpeechProcessor
import com.ailive.audio.WakeWordDetector
import com.ailive.camera.CameraManager
import com.ailive.core.AILiveCore
import com.ailive.settings.AISettings

class MainActivity : AppCompatActivity() {

    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var aiLiveCore: AILiveCore
    private lateinit var cameraManager: CameraManager
    private lateinit var audioManager: AudioManager
    private lateinit var speechProcessor: SpeechProcessor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Register file picker launcher early in lifecycle BEFORE RESUMED state
        filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // handle file pick result
        }

        // Initialize managers
        aiLiveCore = AILiveCore.getInstance(this)
        cameraManager = CameraManager(this)
        audioManager = AudioManager(this)
        speechProcessor = SpeechProcessor(this)

        // Initialize UI views and listeners...
    }

    override fun onStart() {
        super.onStart()
        // Register tools or observers here, earlier than onResume
        aiLiveCore.personalityEngine.registerTool(visionTool)
        aiLiveCore.personalityEngine.registerTool(patternAnalysisTool)
        aiLiveCore.personalityEngine.registerTool(feedbackTrackingTool)
    }
    
    // other methods go here...

}
// [Continuing MainActivity backup content]
// Continue adding the remaining functions, variables, and methods from your backup file
// For example:

    override fun onResume() {
        super.onResume()
        // Any necessary onResume implementation...
    }

    override fun onPause() {
        super.onPause()
        // Any necessary onPause implementation...
    }

    // Other lifecycle methods and MainActivity logic...

// End of the backup content to replace existing buggy MainActivity.kt fully
