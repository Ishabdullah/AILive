package com.ailive.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ailive.R
import com.ailive.ai.llm.ModelSettings

/**
 * Model Settings Activity
 *
 * Allows users to configure LLM inference parameters:
 * - Sampling parameters (temperature, top_p, top_k, penalties)
 * - Generation parameters (context size, max tokens)
 * - Advanced features (Mirostat sampling)
 *
 * Real-time RAM usage estimation warns users if settings may cause instability.
 * All settings are persisted to SharedPreferences.
 */
class ModelSettingsActivity : AppCompatActivity() {
    private val TAG = "ModelSettingsActivity"

    // UI Components
    private lateinit var tvRamUsage: TextView
    private lateinit var tvRamWarning: TextView

    private lateinit var labelTemperature: TextView
    private lateinit var sliderTemperature: SeekBar
    private lateinit var labelTopP: TextView
    private lateinit var sliderTopP: SeekBar
    private lateinit var labelTopK: TextView
    private lateinit var sliderTopK: SeekBar
    private lateinit var labelRepetitionPenalty: TextView
    private lateinit var sliderRepetitionPenalty: SeekBar
    private lateinit var labelPresencePenalty: TextView
    private lateinit var sliderPresencePenalty: SeekBar
    private lateinit var labelFrequencyPenalty: TextView
    private lateinit var sliderFrequencyPenalty: SeekBar
    private lateinit var labelCtxSize: TextView
    private lateinit var sliderCtxSize: SeekBar
    private lateinit var labelMaxTokens: TextView
    private lateinit var sliderMaxTokens: SeekBar
    private lateinit var spinnerMirostat: Spinner

    private lateinit var btnSave: Button
    private lateinit var btnReset: Button
    private lateinit var btnClose: Button

    // Current settings
    private var settings: ModelSettings = ModelSettings.getDefaults()
    private var totalDeviceRamMB: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_settings)

        // Get device RAM
        totalDeviceRamMB = getTotalRAM()

        // Initialize UI
        initializeViews()
        loadSettings()
        setupListeners()
        updateUI()
    }

    private fun initializeViews() {
        // RAM indicator
        tvRamUsage = findViewById(R.id.tvRamUsage)
        tvRamWarning = findViewById(R.id.tvRamWarning)

        // Sliders
        labelTemperature = findViewById(R.id.labelTemperature)
        sliderTemperature = findViewById(R.id.sliderTemperature)
        labelTopP = findViewById(R.id.labelTopP)
        sliderTopP = findViewById(R.id.sliderTopP)
        labelTopK = findViewById(R.id.labelTopK)
        sliderTopK = findViewById(R.id.sliderTopK)
        labelRepetitionPenalty = findViewById(R.id.labelRepetitionPenalty)
        sliderRepetitionPenalty = findViewById(R.id.sliderRepetitionPenalty)
        labelPresencePenalty = findViewById(R.id.labelPresencePenalty)
        sliderPresencePenalty = findViewById(R.id.sliderPresencePenalty)
        labelFrequencyPenalty = findViewById(R.id.labelFrequencyPenalty)
        sliderFrequencyPenalty = findViewById(R.id.sliderFrequencyPenalty)
        labelCtxSize = findViewById(R.id.labelCtxSize)
        sliderCtxSize = findViewById(R.id.sliderCtxSize)
        labelMaxTokens = findViewById(R.id.labelMaxTokens)
        sliderMaxTokens = findViewById(R.id.sliderMaxTokens)

        // Mirostat spinner
        spinnerMirostat = findViewById(R.id.spinnerMirostat)
        val mirostatOptions = arrayOf("Disabled (0)", "Mirostat v1 (1)", "Mirostat v2 (2)")
        spinnerMirostat.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mirostatOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        // Buttons
        btnSave = findViewById(R.id.btnSave)
        btnReset = findViewById(R.id.btnReset)
        btnClose = findViewById(R.id.btnClose)
    }

    private fun loadSettings() {
        settings = ModelSettings.load(this)
    }

    private fun setupListeners() {
        // Temperature: 0.1 to 2.0 (slider 0-190 → 0.1-2.0)
        sliderTemperature.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                settings.temperature = 0.1f + (progress / 100f)
                labelTemperature.text = "Temperature: ${String.format("%.2f", settings.temperature)}"
                updateRamUsage()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Top P: 0.1 to 1.0 (slider 0-90 → 0.1-1.0)
        sliderTopP.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                settings.topP = 0.1f + (progress / 100f)
                labelTopP.text = "Top P: ${String.format("%.2f", settings.topP)}"
                updateRamUsage()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Top K: 1 to 100 (slider 0-99 → 1-100)
        sliderTopK.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                settings.topK = progress + 1
                labelTopK.text = "Top K: ${settings.topK}"
                updateRamUsage()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Repetition Penalty: 1.0 to 1.5 (slider 0-50 → 1.0-1.5)
        sliderRepetitionPenalty.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                settings.repetitionPenalty = 1.0f + (progress / 100f)
                labelRepetitionPenalty.text = "Repetition Penalty: ${String.format("%.2f", settings.repetitionPenalty)}"
                updateRamUsage()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Presence Penalty: 0.0 to 2.0 (slider 0-200 → 0.0-2.0)
        sliderPresencePenalty.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                settings.presencePenalty = progress / 100f
                labelPresencePenalty.text = "Presence Penalty: ${String.format("%.2f", settings.presencePenalty)}"
                updateRamUsage()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Frequency Penalty: 0.0 to 2.0 (slider 0-200 → 0.0-2.0)
        sliderFrequencyPenalty.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                settings.frequencyPenalty = progress / 100f
                labelFrequencyPenalty.text = "Frequency Penalty: ${String.format("%.2f", settings.frequencyPenalty)}"
                updateRamUsage()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Context Size: 512, 1024, 2048, 4096, 8192 (slider 0-15 → preset values)
        sliderCtxSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                settings.ctxSize = getCtxSizeFromSlider(progress)
                labelCtxSize.text = "Context Size: ${settings.ctxSize} tokens"
                updateRamUsage()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Max Tokens: 50, 128, 256, 512, 1024, 2048 (slider 0-39 → preset values)
        sliderMaxTokens.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                settings.maxTokens = getMaxTokensFromSlider(progress)
                labelMaxTokens.text = "Max Tokens: ${settings.maxTokens}"
                updateRamUsage()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Save button
        btnSave.setOnClickListener {
            saveSettings()
        }

        // Reset button
        btnReset.setOnClickListener {
            resetToDefaults()
        }

        // Close button
        btnClose.setOnClickListener {
            finish()
        }
    }

    private fun updateUI() {
        // Set slider positions from settings
        sliderTemperature.progress = ((settings.temperature - 0.1f) * 100).toInt()
        sliderTopP.progress = ((settings.topP - 0.1f) * 100).toInt()
        sliderTopK.progress = settings.topK - 1
        sliderRepetitionPenalty.progress = ((settings.repetitionPenalty - 1.0f) * 100).toInt()
        sliderPresencePenalty.progress = (settings.presencePenalty * 100).toInt()
        sliderFrequencyPenalty.progress = (settings.frequencyPenalty * 100).toInt()
        sliderCtxSize.progress = getSliderFromCtxSize(settings.ctxSize)
        sliderMaxTokens.progress = getSliderFromMaxTokens(settings.maxTokens)
        spinnerMirostat.setSelection(settings.mirostat)

        // Update labels
        labelTemperature.text = "Temperature: ${String.format("%.2f", settings.temperature)}"
        labelTopP.text = "Top P: ${String.format("%.2f", settings.topP)}"
        labelTopK.text = "Top K: ${settings.topK}"
        labelRepetitionPenalty.text = "Repetition Penalty: ${String.format("%.2f", settings.repetitionPenalty)}"
        labelPresencePenalty.text = "Presence Penalty: ${String.format("%.2f", settings.presencePenalty)}"
        labelFrequencyPenalty.text = "Frequency Penalty: ${String.format("%.2f", settings.frequencyPenalty)}"
        labelCtxSize.text = "Context Size: ${settings.ctxSize} tokens"
        labelMaxTokens.text = "Max Tokens: ${settings.maxTokens}"

        updateRamUsage()
    }

    private fun updateRamUsage() {
        val estimatedRamMB = settings.estimateRamUsageMB()
        val estimatedRamGB = estimatedRamMB / 1024f
        val totalRamGB = totalDeviceRamMB / 1024f

        tvRamUsage.text = "${String.format("%.1f", estimatedRamGB)} GB / ${String.format("%.1f", totalRamGB)} GB"

        // Check if safe and show warning
        val warning = settings.getWarningMessage(totalDeviceRamMB)
        if (warning != null) {
            tvRamWarning.text = warning
            tvRamWarning.visibility = View.VISIBLE
            tvRamUsage.setTextColor(0xFFFF5500.toInt()) // Orange warning color
        } else {
            tvRamWarning.visibility = View.GONE
            tvRamUsage.setTextColor(0xFF00FF00.toInt()) // Green safe color
        }
    }

    private fun saveSettings() {
        // Get mirostat selection
        settings.mirostat = spinnerMirostat.selectedItemPosition

        // Validate and clamp values
        settings = settings.validate()

        // Check if safe
        if (!settings.isSafeForDevice(totalDeviceRamMB)) {
            Toast.makeText(
                this,
                "⚠️ Warning: High RAM usage may cause instability",
                Toast.LENGTH_LONG
            ).show()
        }

        // Save to SharedPreferences
        settings.save(this)

        Toast.makeText(this, "✓ Settings saved successfully!", Toast.LENGTH_SHORT).show()

        // Return to main activity
        finish()
    }

    private fun resetToDefaults() {
        settings = ModelSettings.getDefaults()
        updateUI()
        Toast.makeText(this, "Reset to v1.1 optimized defaults", Toast.LENGTH_SHORT).show()
    }

    /**
     * Get total device RAM in MB
     */
    private fun getTotalRAM(): Int {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return (memInfo.totalMem / (1024 * 1024)).toInt()
    }

    /**
     * Context size slider mapping
     * 0-3: 512
     * 4-7: 1024
     * 8-11: 2048
     * 12-15: 4096
     * 16+: 8192
     */
    private fun getCtxSizeFromSlider(progress: Int): Int {
        return when {
            progress <= 3 -> 512
            progress <= 7 -> 1024
            progress <= 11 -> 2048
            progress <= 15 -> 4096
            else -> 8192
        }
    }

    private fun getSliderFromCtxSize(ctxSize: Int): Int {
        return when (ctxSize) {
            512 -> 1
            1024 -> 5
            2048 -> 9
            4096 -> 13
            8192 -> 17
            else -> 13 // Default to 4096
        }
    }

    /**
     * Max tokens slider mapping
     * 0-9: 50 to 500 (step 50)
     * 10-19: 550 to 1000 (step 50)
     * 20-29: 1100 to 1500 (step 50)
     * 30-39: 1550 to 2048 (step 50)
     */
    private fun getMaxTokensFromSlider(progress: Int): Int {
        return when {
            progress <= 9 -> 50 + (progress * 50)
            progress <= 19 -> 500 + ((progress - 9) * 50)
            progress <= 29 -> 1000 + ((progress - 19) * 50)
            else -> 1500 + ((progress - 29) * 50).coerceAtMost(548) // Max 2048
        }
    }

    private fun getSliderFromMaxTokens(maxTokens: Int): Int {
        return when {
            maxTokens <= 500 -> (maxTokens / 50) - 1
            maxTokens <= 1000 -> 9 + ((maxTokens - 500) / 50)
            maxTokens <= 1500 -> 19 + ((maxTokens - 1000) / 50)
            else -> 29 + ((maxTokens - 1500) / 50)
        }.coerceIn(0, 39)
    }
}
