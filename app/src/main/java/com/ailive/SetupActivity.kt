package com.ailive

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ailive.audio.VoiceRecorder
import com.ailive.settings.AISettings

class SetupActivity : AppCompatActivity() {
    // Initialize immediately to avoid lateinit crash
    private var settings: AISettings? = null
    private var recorder: VoiceRecorder? = null
    
    private lateinit var aiNameInput: EditText
    private lateinit var wakePhraseInput: EditText
    private lateinit var nameRecordingStatus: TextView
    private lateinit var wakeRecordingStatus: TextView
    private lateinit var recordNameButton: Button
    private lateinit var recordWakeButton: Button
    private lateinit var finishButton: Button
    
    private var nameSampleCount = 0
    private var wakeSampleCount = 0
    private val REQUIRED_SAMPLES = 3
    
    companion object {
        private const val REQUEST_AUDIO_PERMISSION = 200
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // Initialize settings FIRST
            settings = AISettings(this)
            recorder = VoiceRecorder(this)
            
            setContentView(R.layout.activity_setup)
            initializeUI()
            checkPermissions()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Setup error: ${e.message}", Toast.LENGTH_LONG).show()
            // Skip setup on error
            if (settings == null) {
                settings = AISettings(this)
            }
            settings?.isSetupComplete = true
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
    
    private fun initializeUI() {
        aiNameInput = findViewById(R.id.aiNameInput)
        wakePhraseInput = findViewById(R.id.wakePhraseInput)
        nameRecordingStatus = findViewById(R.id.nameRecordingStatus)
        wakeRecordingStatus = findViewById(R.id.wakeRecordingStatus)
        recordNameButton = findViewById(R.id.recordNameButton)
        recordWakeButton = findViewById(R.id.recordWakeButton)
        finishButton = findViewById(R.id.finishSetupButton)
        
        recordNameButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> startNameRecording()
                MotionEvent.ACTION_UP -> stopNameRecording()
            }
            true
        }
        
        recordWakeButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> startWakeRecording()
                MotionEvent.ACTION_UP -> stopWakeRecording()
            }
            true
        }
        
        finishButton.setOnClickListener {
            finishSetup()
        }
    }
    
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_AUDIO_PERMISSION
            )
        }
    }
    
    private fun startNameRecording() {
        val name = aiNameInput.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Enter a name first!", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (nameSampleCount >= REQUIRED_SAMPLES) return
        
        val filename = "name_sample_$nameSampleCount.3gp"
        recorder?.startRecording(filename)
        recordNameButton.text = "RECORDING..."
    }
    
    private fun stopNameRecording() {
        if (recorder?.isRecording() != true) return
        
        recorder?.stopRecording()
        nameSampleCount++
        settings?.addNameSample(nameSampleCount - 1)
        
        recordNameButton.text = "HOLD TO RECORD NAME"
        nameRecordingStatus.text = "Recorded: $nameSampleCount/$REQUIRED_SAMPLES"
        
        if (nameSampleCount >= REQUIRED_SAMPLES) {
            Toast.makeText(this, "Name complete!", Toast.LENGTH_SHORT).show()
            checkSetupComplete()
        }
    }
    
    private fun startWakeRecording() {
        val phrase = wakePhraseInput.text.toString().trim()
        if (phrase.isEmpty()) {
            Toast.makeText(this, "Enter wake phrase first!", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (wakeSampleCount >= REQUIRED_SAMPLES) return
        
        val filename = "wake_sample_$wakeSampleCount.3gp"
        recorder?.startRecording(filename)
        recordWakeButton.text = "RECORDING..."
    }
    
    private fun stopWakeRecording() {
        if (recorder?.isRecording() != true) return
        
        recorder?.stopRecording()
        wakeSampleCount++
        settings?.addWakeSample(wakeSampleCount - 1)
        
        recordWakeButton.text = "HOLD TO RECORD WAKE"
        wakeRecordingStatus.text = "Recorded: $wakeSampleCount/$REQUIRED_SAMPLES"
        
        if (wakeSampleCount >= REQUIRED_SAMPLES) {
            Toast.makeText(this, "Wake phrase complete!", Toast.LENGTH_SHORT).show()
            checkSetupComplete()
        }
    }
    
    private fun checkSetupComplete() {
        finishButton.isEnabled = 
            nameSampleCount >= REQUIRED_SAMPLES && 
            wakeSampleCount >= REQUIRED_SAMPLES &&
            aiNameInput.text.isNotEmpty() &&
            wakePhraseInput.text.isNotEmpty()
    }
    
    private fun finishSetup() {
        settings?.aiName = aiNameInput.text.toString().trim()
        settings?.wakePhrase = wakePhraseInput.text.toString().trim()
        settings?.isSetupComplete = true
        
        Toast.makeText(this, "Setup complete!", Toast.LENGTH_SHORT).show()
        
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO_PERMISSION) {
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Audio permission required", Toast.LENGTH_SHORT).show()
                settings?.isSetupComplete = true
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }
}
