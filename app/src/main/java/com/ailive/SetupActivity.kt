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

/**
 * SetupActivity - Personalize AI name and wake word
 */
class SetupActivity : AppCompatActivity() {
    private val TAG = "SetupActivity"
    
    private lateinit var settings: AISettings
    private lateinit var recorder: VoiceRecorder
    
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
        private val PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        
        settings = AISettings(this)
        recorder = VoiceRecorder(this)
        
        initializeUI()
        checkPermissions()
    }
    
    private fun initializeUI() {
        aiNameInput = findViewById(R.id.aiNameInput)
        wakePhraseInput = findViewById(R.id.wakePhraseInput)
        nameRecordingStatus = findViewById(R.id.nameRecordingStatus)
        wakeRecordingStatus = findViewById(R.id.wakeRecordingStatus)
        recordNameButton = findViewById(R.id.recordNameButton)
        recordWakeButton = findViewById(R.id.recordWakeButton)
        finishButton = findViewById(R.id.finishSetupButton)
        
        // Hold-to-record for name
        recordNameButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> startNameRecording()
                MotionEvent.ACTION_UP -> stopNameRecording()
            }
            true
        }
        
        // Hold-to-record for wake phrase
        recordWakeButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> startWakeRecording()
                MotionEvent.ACTION_UP -> stopWakeRecording()
            }
            true
        }
        
        // Finish setup
        finishButton.setOnClickListener {
            finishSetup()
        }
    }
    
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_AUDIO_PERMISSION)
        }
    }
    
    private fun startNameRecording() {
        val name = aiNameInput.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Enter a name first!", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (nameSampleCount >= REQUIRED_SAMPLES) {
            Toast.makeText(this, "Already recorded 3 samples!", Toast.LENGTH_SHORT).show()
            return
        }
        
        val filename = "name_sample_$nameSampleCount.3gp"
        if (recorder.startRecording(filename)) {
            recordNameButton.text = "🔴 RECORDING..."
            recordNameButton.backgroundTintList = 
                ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)
        }
    }
    
    private fun stopNameRecording() {
        if (!recorder.isRecording()) return
        
        recorder.stopRecording()
        nameSampleCount++
        settings.addNameSample(nameSampleCount - 1)
        
        recordNameButton.text = "🎤 HOLD TO RECORD NAME"
        recordNameButton.backgroundTintList = 
            ContextCompat.getColorStateList(this, android.R.color.holo_red_light)
        
        nameRecordingStatus.text = "Recorded: $nameSampleCount/$REQUIRED_SAMPLES"
        
        if (nameSampleCount >= REQUIRED_SAMPLES) {
            Toast.makeText(this, "✓ Name samples complete!", Toast.LENGTH_SHORT).show()
            checkSetupComplete()
        }
    }
    
    private fun startWakeRecording() {
        val phrase = wakePhraseInput.text.toString().trim()
        if (phrase.isEmpty()) {
            Toast.makeText(this, "Enter wake phrase first!", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (wakeSampleCount >= REQUIRED_SAMPLES) {
            Toast.makeText(this, "Already recorded 3 samples!", Toast.LENGTH_SHORT).show()
            return
        }
        
        val filename = "wake_sample_$wakeSampleCount.3gp"
        if (recorder.startRecording(filename)) {
            recordWakeButton.text = "🔴 RECORDING..."
            recordWakeButton.backgroundTintList = 
                ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)
        }
    }
    
    private fun stopWakeRecording() {
        if (!recorder.isRecording()) return
        
        recorder.stopRecording()
        wakeSampleCount++
        settings.addWakeSample(wakeSampleCount - 1)
        
        recordWakeButton.text = "🎤 HOLD TO RECORD WAKE PHRASE"
        recordWakeButton.backgroundTintList = 
            ContextCompat.getColorStateList(this, android.R.color.holo_red_light)
        
        wakeRecordingStatus.text = "Recorded: $wakeSampleCount/$REQUIRED_SAMPLES"
        
        if (wakeSampleCount >= REQUIRED_SAMPLES) {
            Toast.makeText(this, "✓ Wake phrase samples complete!", Toast.LENGTH_SHORT).show()
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
        settings.aiName = aiNameInput.text.toString().trim()
        settings.wakePhrase = wakePhraseInput.text.toString().trim()
        settings.isSetupComplete = true
        
        Toast.makeText(this, "✓ Setup complete! Meet ${settings.aiName}!", Toast.LENGTH_LONG).show()
        
        // Go to main activity
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
                Toast.makeText(this, "Audio permission required!", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}
