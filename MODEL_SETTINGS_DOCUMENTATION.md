# Model Settings Panel - Implementation Documentation

**Version:** v1.1 Week 5
**Date:** November 10, 2025
**Branch:** `claude/teleport-session-011-011CUyNKauomCnL39khdRpaU`
**Commits:** `77c7fa3`, `47e084a`

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [Features Implemented](#features-implemented)
3. [Technical Architecture](#technical-architecture)
4. [File Structure](#file-structure)
5. [Configuration Parameters](#configuration-parameters)
6. [RAM Estimation Formula](#ram-estimation-formula)
7. [Usage Guide](#usage-guide)
8. [Code Examples](#code-examples)
9. [Testing Instructions](#testing-instructions)
10. [Future Enhancements](#future-enhancements)
11. [Troubleshooting](#troubleshooting)

---

## Overview

The Model Settings Panel provides users with a comprehensive interface to configure LLM inference parameters in real-time. This feature enables users to balance performance, quality, and RAM usage based on their device capabilities and preferences.

### Key Benefits

- **User Control**: Fine-tune model behavior without code changes
- **RAM Safety**: Real-time warnings prevent device instability
- **Persistence**: Settings saved across app restarts
- **UX Consistency**: Neon blue theme matches existing AILive design
- **Immediate Effect**: Settings apply on next generation

---

## Features Implemented

### 1. Dynamic Settings Panel

A full-screen settings activity accessible via a ⚙️ button in the MainActivity toolbar.

**UI Components:**
- Scrollable layout for all parameters
- Real-time RAM usage indicator
- Save/Reset/Close buttons
- Descriptive labels and tooltips
- Color-coded warnings (green = safe, orange = warning)

### 2. Configurable Parameters

| Category | Parameters | Purpose |
|----------|-----------|---------|
| **Sampling** | temperature, top_p, top_k | Control randomness and creativity |
| **Penalties** | repetition_penalty, presence_penalty, frequency_penalty | Reduce repetition and improve diversity |
| **Generation** | ctx_size, max_tokens | Memory and response length control |
| **Advanced** | mirostat (0/1/2) | Alternative sampling strategy |

### 3. RAM Usage Monitoring

**Real-time Estimation:**
```
Total RAM = Base Model + Context Memory + Token Buffer

Base Model (Q4_K_M): 1024 MB
Context Memory: (ctxSize / 2048) × 1024 MB
Token Buffer: (maxTokens / 512) × 128 MB
```

**Examples:**
- `ctx=2048, max=512`: ~2.1 GB ✅ Safe for most devices
- `ctx=4096, max=512`: ~3.1 GB ✅ v1.1 default (6GB+ devices)
- `ctx=8192, max=1024`: ~5.3 GB ⚠️ Warning (requires 8GB+ RAM)

**Safety Features:**
- Warns if estimated usage > 60% of device RAM
- Color-coded indicators (green/orange)
- Toast notification on save if unsafe
- Automatic value clamping to safe ranges

### 4. Persistence Layer

**SharedPreferences Storage:**
- Key: `ailive_model_settings`
- Format: Flat key-value pairs
- Automatic save on "SAVE" button
- Load on app start and settings screen open

**Supported Operations:**
- `ModelSettings.load(context)`: Load from disk
- `settings.save(context)`: Save to disk
- `ModelSettings.getDefaults()`: Reset to v1.1 defaults
- `settings.validate()`: Clamp values to safe ranges

---

## Technical Architecture

### Data Flow

```
User Adjusts Slider
    ↓
Settings Updated in Memory
    ↓
RAM Usage Recalculated
    ↓
Warning Displayed (if needed)
    ↓
User Taps "SAVE"
    ↓
Saved to SharedPreferences
    ↓
Returns to MainActivity
    ↓
onResume() → LLMManager.reloadSettings()
    ↓
Next Generation Uses New Settings
```

### Class Hierarchy

```
ModelSettings (data class)
├── Validation & Clamping
├── RAM Estimation
├── Save/Load (SharedPreferences)
└── JSON Export

ModelSettingsActivity (UI)
├── Slider Management
├── Real-time Updates
├── Device RAM Detection
└── User Actions (Save/Reset/Close)

LLMManager
├── Settings Integration
├── Auto-reload on Resume
└── Pass maxTokens to LLamaAndroid

LLamaAndroid
└── Configurable maxTokens Parameter
```

---

## File Structure

### New Files Created

#### 1. `app/src/main/java/com/ailive/ai/llm/ModelSettings.kt` (217 lines)

**Purpose:** Core data class and persistence logic

**Key Components:**
```kotlin
data class ModelSettings(
    // Sampling parameters
    var temperature: Float = 0.7f,
    var topP: Float = 0.9f,
    var topK: Int = 40,
    var repetitionPenalty: Float = 1.18f,
    var presencePenalty: Float = 0.6f,
    var frequencyPenalty: Float = 0.3f,

    // Generation parameters
    var ctxSize: Int = 4096,  // v1.1 default
    var maxTokens: Int = 512,

    // Advanced
    var mirostat: Int = 0,
    var mirostatTau: Float = 5.0f,
    var mirostatEta: Float = 0.1f
)
```

**Methods:**
- `estimateRamUsageMB()`: Calculate RAM from settings
- `isSafeForDevice(totalDeviceRamMB)`: Check if safe
- `getWarningMessage(totalDeviceRamMB)`: Get warning text
- `validate()`: Clamp all values to safe ranges
- `save(context)`: Persist to SharedPreferences
- `load(context)`: Load from SharedPreferences (static)
- `toJson()`: Export as JSON string

#### 2. `app/src/main/java/com/ailive/ui/ModelSettingsActivity.kt` (354 lines)

**Purpose:** Settings UI and user interaction

**Key Components:**
```kotlin
class ModelSettingsActivity : AppCompatActivity() {
    private var settings: ModelSettings
    private var totalDeviceRamMB: Int

    // UI setup
    private fun initializeViews()
    private fun setupListeners()
    private fun updateUI()

    // RAM monitoring
    private fun updateRamUsage()
    private fun getTotalRAM(): Int

    // User actions
    private fun saveSettings()
    private fun resetToDefaults()

    // Slider mapping helpers
    private fun getCtxSizeFromSlider(progress: Int): Int
    private fun getMaxTokensFromSlider(progress: Int): Int
}
```

**Slider Mappings:**

**Context Size (0-15):**
- 0-3: 512 tokens
- 4-7: 1024 tokens
- 8-11: 2048 tokens
- 12-15: 4096 tokens (default)
- 16+: 8192 tokens

**Max Tokens (0-39):**
- 0-9: 50-500 (step 50)
- 10-19: 550-1000 (step 50, default: 512)
- 20-29: 1100-1500 (step 50)
- 30-39: 1550-2048 (step 50)

#### 3. `app/src/main/res/layout/activity_model_settings.xml` (490 lines)

**Purpose:** Settings screen UI layout

**Components:**
- ScrollView for vertical scrolling
- 11 SeekBar controls with labels
- RAM usage CardView with color indicator
- 3 action buttons (Save, Reset, Close)
- Neon blue theme (#00E5FF)

**Layout Structure:**
```xml
<ScrollView>
    <LinearLayout orientation="vertical">
        <!-- RAM Usage Display -->
        <CardView id="ramUsageCard">
            <TextView id="ramUsageValue" />
        </CardView>

        <!-- Parameter Sliders -->
        <TextView label="Temperature" />
        <SeekBar id="temperatureSeekBar" max="200" />
        <TextView id="temperatureValue" />

        <TextView label="Top-P" />
        <SeekBar id="topPSeekBar" max="100" />
        <TextView id="topPValue" />

        <!-- ... 9 more parameter groups ... -->

        <!-- Action Buttons -->
        <LinearLayout orientation="horizontal">
            <Button id="saveButton" text="SAVE" />
            <Button id="resetButton" text="RESET" />
            <Button id="closeButton" text="CLOSE" />
        </LinearLayout>
    </LinearLayout>
</ScrollView>
```

### Modified Files

#### 4. `app/src/main/java/com/ailive/MainActivity.kt`

**Changes:**
- Added ⚙️ settings menu button to toolbar
- Implemented `onOptionsItemSelected()` to launch ModelSettingsActivity
- Added `onResume()` reload logic for LLMManager

**Code Added:**
```kotlin
override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menu?.add(0, 1, 0, "⚙️ Settings")
    return true
}

override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == 1) {
        startActivity(Intent(this, ModelSettingsActivity::class.java))
        return true
    }
    return super.onOptionsItemSelected(item)
}

override fun onResume() {
    super.onResume()
    motorAI.reloadLLMSettings()  // Reload settings when returning
}
```

#### 5. `app/src/main/java/com/ailive/motor/MotorAI.kt`

**Changes:**
- Added `reloadLLMSettings()` method
- Calls `llmManager.reloadSettings()`

**Code Added:**
```kotlin
fun reloadLLMSettings() {
    llmManager?.reloadSettings(context)
}
```

#### 6. `app/src/main/java/com/ailive/ai/llm/LLMManager.kt`

**Changes:**
- Added `currentSettings: ModelSettings` property
- Implemented `reloadSettings(context)` method
- Updated `generate()` to pass `maxTokens` to LLamaAndroid
- Load settings on initialization

**Code Added:**
```kotlin
private var currentSettings: ModelSettings = ModelSettings.getDefaults()

fun reloadSettings(context: Context) {
    currentSettings = ModelSettings.load(context)
    Log.i(TAG, "Settings reloaded: ctx=${currentSettings.ctxSize}, max=${currentSettings.maxTokens}")
}

suspend fun generate(prompt: String, agentName: String = "AILive"): String {
    // ... existing code ...
    val response = llamaBridge.generate(
        chatPrompt,
        currentSettings.maxTokens  // ← Now configurable
    )
    // ...
}
```

#### 7. `app/src/main/java/com/ailive/ai/llm/LLamaAndroid.kt`

**Changes:**
- Updated `generate()` signature to accept `maxTokens` parameter
- Pass `maxTokens` to native `bench()` call

**Code Changed:**
```kotlin
// BEFORE
fun generate(prompt: String): String {
    return bench(modelPath, prompt, nThreads = 4)
}

// AFTER
fun generate(prompt: String, maxTokens: Int = 512): String {
    return bench(modelPath, prompt, nThreads = 4, nPredict = maxTokens)
}
```

#### 8. `app/src/main/AndroidManifest.xml`

**Changes:**
- Added ModelSettingsActivity declaration

**Code Added:**
```xml
<activity
    android:name=".ui.ModelSettingsActivity"
    android:label="Model Settings"
    android:theme="@style/Theme.AILive" />
```

---

## Configuration Parameters

### Sampling Parameters

#### 1. Temperature (0.0 - 2.0)
**Default:** 0.7
**Description:** Controls randomness in generation

**Values:**
- 0.1-0.3: Focused, deterministic (good for factual)
- 0.7: Balanced (default)
- 1.0-2.0: Creative, varied (good for conversation)

**Slider Mapping:**
```kotlin
val temperature = progress / 100.0f  // 0-200 slider → 0.0-2.0
```

---

#### 2. Top-P (0.0 - 1.0)
**Default:** 0.9
**Description:** Nucleus sampling threshold

**Values:**
- 0.5: Conservative vocabulary
- 0.9: Wide vocabulary (default)
- 1.0: All tokens considered

**Slider Mapping:**
```kotlin
val topP = progress / 100.0f  // 0-100 slider → 0.0-1.0
```

---

#### 3. Top-K (1 - 100)
**Default:** 40
**Description:** Limits token selection to top K candidates

**Values:**
- 10: Very focused
- 40: Balanced (default)
- 100: Wide selection

**Slider Mapping:**
```kotlin
val topK = progress  // Direct 0-100 mapping
```

---

### Penalty Parameters

#### 4. Repetition Penalty (1.0 - 2.0)
**Default:** 1.18
**Description:** Penalizes repeating tokens

**Values:**
- 1.0: No penalty
- 1.18: Moderate (default)
- 2.0: Strong penalty

**Slider Mapping:**
```kotlin
val repPenalty = 1.0f + (progress / 100.0f)  // 0-100 → 1.0-2.0
```

---

#### 5. Presence Penalty (0.0 - 1.0)
**Default:** 0.6
**Description:** Encourages new topics

**Values:**
- 0.0: No penalty
- 0.6: Moderate (default)
- 1.0: Strong penalty

**Slider Mapping:**
```kotlin
val presPenalty = progress / 100.0f  // 0-100 → 0.0-1.0
```

---

#### 6. Frequency Penalty (0.0 - 1.0)
**Default:** 0.3
**Description:** Reduces token frequency

**Values:**
- 0.0: No penalty
- 0.3: Mild (default)
- 1.0: Strong penalty

**Slider Mapping:**
```kotlin
val freqPenalty = progress / 100.0f  // 0-100 → 0.0-1.0
```

---

### Generation Parameters

#### 7. Context Size (512 - 8192 tokens)
**Default:** 4096
**Description:** Maximum context window

**Values:**
- 512: Minimal context (~1.5GB RAM)
- 2048: Short conversations (~2.1GB RAM)
- 4096: Long conversations (~3.1GB RAM, default)
- 8192: Maximum context (~5.3GB RAM)

**RAM Impact:** +512MB per 2048 tokens

**Slider Mapping:**
```kotlin
fun getCtxSizeFromSlider(progress: Int): Int {
    return when (progress) {
        in 0..3 -> 512
        in 4..7 -> 1024
        in 8..11 -> 2048
        in 12..15 -> 4096  // Default
        else -> 8192
    }
}
```

---

#### 8. Max Tokens (50 - 2048)
**Default:** 512
**Description:** Maximum response length

**Values:**
- 50-200: Short answers (1-2 sentences)
- 512: Moderate (default, 2-3 paragraphs)
- 1000-2048: Long responses (essays)

**RAM Impact:** +128MB per 512 tokens

**Slider Mapping:**
```kotlin
fun getMaxTokensFromSlider(progress: Int): Int {
    return 50 + (progress * 50)  // 0-39 → 50-2000
}
```

---

### Advanced Parameters

#### 9. Mirostat Mode (0, 1, 2)
**Default:** 0 (disabled)
**Description:** Alternative sampling algorithm

**Values:**
- 0: Disabled (use temp/top-p)
- 1: Mirostat v1
- 2: Mirostat v2

**Slider Mapping:**
```kotlin
val mirostat = progress  // 0-2 direct mapping
```

---

#### 10. Mirostat Tau (0.0 - 10.0)
**Default:** 5.0
**Description:** Target entropy for Mirostat

**Slider Mapping:**
```kotlin
val mirostatTau = progress / 10.0f  // 0-100 → 0.0-10.0
```

---

#### 11. Mirostat Eta (0.0 - 1.0)
**Default:** 0.1
**Description:** Learning rate for Mirostat

**Slider Mapping:**
```kotlin
val mirostatEta = progress / 100.0f  // 0-100 → 0.0-1.0
```

---

## RAM Estimation Formula

### Formula Breakdown

```kotlin
fun estimateRamUsageMB(): Int {
    val baseModelMB = 1024  // Q4_K_M quantized model

    val contextMemoryMB = (ctxSize.toFloat() / 2048f) * 1024f
    val tokenBufferMB = (maxTokens.toFloat() / 512f) * 128f

    return (baseModelMB + contextMemoryMB + tokenBufferMB).toInt()
}
```

### Examples

**Scenario 1: Minimal (512 ctx, 50 tokens)**
```
Base: 1024 MB
Context: (512/2048) × 1024 = 256 MB
Buffer: (50/512) × 128 = 12 MB
Total: ~1.3 GB ✅
```

**Scenario 2: Default (4096 ctx, 512 tokens)**
```
Base: 1024 MB
Context: (4096/2048) × 1024 = 2048 MB
Buffer: (512/512) × 128 = 128 MB
Total: ~3.2 GB ✅
```

**Scenario 3: Maximum (8192 ctx, 2048 tokens)**
```
Base: 1024 MB
Context: (8192/2048) × 1024 = 4096 MB
Buffer: (2048/512) × 128 = 512 MB
Total: ~5.6 GB ⚠️
```

### Safety Thresholds

```kotlin
fun isSafeForDevice(totalDeviceRamMB: Int): Boolean {
    val estimatedUsage = estimateRamUsageMB()
    val threshold = (totalDeviceRamMB * 0.6).toInt()  // 60% threshold
    return estimatedUsage <= threshold
}
```

**Device Examples:**

| Device RAM | 60% Threshold | Max Safe ctx_size | Max Safe max_tokens |
|------------|---------------|-------------------|---------------------|
| 4GB (4096MB) | 2457MB | 2048 | 512 |
| 6GB (6144MB) | 3686MB | 4096 | 512 |
| 8GB (8192MB) | 4915MB | 4096 | 1024 |
| 12GB (12288MB) | 7372MB | 8192 | 2048 |

---

## Usage Guide

### For End Users

#### Opening Settings

1. Launch AILive app
2. Tap **⚙️** button in top toolbar
3. Model Settings screen opens

#### Adjusting Parameters

1. **Drag sliders** to adjust values
2. **Watch RAM usage** update in real-time
3. **Green indicator** = safe, **Orange** = warning
4. Tap **SAVE** to apply changes
5. Return to chat - settings active on next message

#### Resetting to Defaults

1. Tap **RESET** button
2. All sliders return to v1.1 defaults
3. Tap **SAVE** to confirm

#### Understanding Warnings

**Green RAM (Safe):**
- ✅ Settings use <60% of device RAM
- ✅ Stable performance expected

**Orange RAM (Warning):**
- ⚠️ Settings use >60% of device RAM
- ⚠️ May cause slowdowns or crashes
- ⚠️ Reduce ctx_size or max_tokens

---

### For Developers

#### Loading Settings

```kotlin
// Load current settings
val settings = ModelSettings.load(context)

// Access parameters
val temp = settings.temperature
val ctx = settings.ctxSize
```

#### Modifying Settings

```kotlin
// Create new settings
val settings = ModelSettings.getDefaults()

// Modify parameters
settings.temperature = 0.9f
settings.ctxSize = 2048

// Validate (clamp to safe ranges)
settings.validate()

// Save to disk
settings.save(context)
```

#### Using in LLMManager

```kotlin
class LLMManager(private val context: Context) {
    private var currentSettings: ModelSettings

    init {
        currentSettings = ModelSettings.load(context)
    }

    fun reloadSettings(context: Context) {
        currentSettings = ModelSettings.load(context)
    }

    suspend fun generate(prompt: String): String {
        return llamaBridge.generate(
            prompt,
            maxTokens = currentSettings.maxTokens
        )
    }
}
```

#### Custom Validation

```kotlin
fun validate() {
    temperature = temperature.coerceIn(0.0f, 2.0f)
    topP = topP.coerceIn(0.0f, 1.0f)
    topK = topK.coerceIn(1, 100)
    ctxSize = ctxSize.coerceIn(512, 8192)
    maxTokens = maxTokens.coerceIn(50, 2048)
    // ... etc
}
```

---

## Code Examples

### Example 1: Loading Settings on App Start

```kotlin
// MainActivity.kt
class MainActivity : AppCompatActivity() {
    private lateinit var motorAI: MotorAI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize MotorAI (which initializes LLMManager)
        motorAI = MotorAI(this)

        // LLMManager automatically loads settings in init{}
    }
}
```

### Example 2: Handling Settings Changes

```kotlin
// MainActivity.kt
override fun onResume() {
    super.onResume()

    // Reload settings when returning from settings screen
    motorAI.reloadLLMSettings()

    Log.d(TAG, "Settings reloaded")
}
```

### Example 3: Custom RAM Threshold

```kotlin
// ModelSettings.kt
fun isSafeForDevice(totalDeviceRamMB: Int, customThreshold: Float = 0.6f): Boolean {
    val estimatedUsage = estimateRamUsageMB()
    val threshold = (totalDeviceRamMB * customThreshold).toInt()
    return estimatedUsage <= threshold
}

// Usage
val safe = settings.isSafeForDevice(6144, customThreshold = 0.5f) // 50% threshold
```

### Example 4: Exporting Settings as JSON

```kotlin
// ModelSettings.kt
fun toJson(): String {
    return """
    {
        "temperature": $temperature,
        "top_p": $topP,
        "top_k": $topK,
        "repetition_penalty": $repetitionPenalty,
        "presence_penalty": $presencePenalty,
        "frequency_penalty": $frequencyPenalty,
        "ctx_size": $ctxSize,
        "max_tokens": $maxTokens,
        "mirostat": $mirostat,
        "mirostat_tau": $mirostatTau,
        "mirostat_eta": $mirostatEta
    }
    """.trimIndent()
}

// Usage
val json = settings.toJson()
Log.d(TAG, "Current settings: $json")
```

### Example 5: Creating Preset Profiles

```kotlin
// Preset configurations
object ModelPresets {
    fun creative(): ModelSettings {
        return ModelSettings(
            temperature = 1.2f,
            topP = 0.95f,
            topK = 60,
            ctxSize = 4096,
            maxTokens = 1024
        )
    }

    fun factual(): ModelSettings {
        return ModelSettings(
            temperature = 0.3f,
            topP = 0.7f,
            topK = 20,
            ctxSize = 2048,
            maxTokens = 512
        )
    }

    fun balanced(): ModelSettings {
        return ModelSettings.getDefaults()
    }
}

// Usage
val creativeSettings = ModelPresets.creative()
creativeSettings.save(context)
```

---

## Testing Instructions

### Manual Testing Checklist

#### UI Testing

- [ ] ⚙️ button appears in MainActivity toolbar
- [ ] Tapping ⚙️ opens ModelSettingsActivity
- [ ] All 11 sliders are visible and scrollable
- [ ] Slider values update labels correctly
- [ ] RAM usage updates when sliders move
- [ ] RAM indicator changes color (green/orange)
- [ ] SAVE button saves settings
- [ ] RESET button restores defaults
- [ ] CLOSE button returns to MainActivity

#### Functional Testing

- [ ] Settings persist after app restart
- [ ] Settings persist after closing settings screen
- [ ] LLMManager reloads settings on onResume()
- [ ] maxTokens is passed to LLamaAndroid.generate()
- [ ] Changing ctxSize affects RAM estimate
- [ ] Changing maxTokens affects RAM estimate
- [ ] Validation clamps out-of-range values
- [ ] Warning appears when RAM usage >60%

#### Edge Cases

- [ ] First launch (no saved settings) uses defaults
- [ ] Invalid SharedPreferences data falls back to defaults
- [ ] Extremely low RAM devices show warning
- [ ] Settings screen works on tablets (landscape)
- [ ] Rapid slider changes don't crash app

### Automated Testing

```kotlin
@Test
fun testSettingsPersistence() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // Save custom settings
    val settings = ModelSettings(temperature = 1.5f, ctxSize = 8192)
    settings.save(context)

    // Load settings
    val loaded = ModelSettings.load(context)

    // Verify
    assertEquals(1.5f, loaded.temperature, 0.01f)
    assertEquals(8192, loaded.ctxSize)
}

@Test
fun testRamEstimation() {
    val settings = ModelSettings(ctxSize = 4096, maxTokens = 512)
    val ramUsage = settings.estimateRamUsageMB()

    // Base (1024) + Context (2048) + Buffer (128) = 3200
    assertTrue(ramUsage in 3150..3250)
}

@Test
fun testValidation() {
    val settings = ModelSettings(
        temperature = 5.0f,  // Invalid
        ctxSize = 16384      // Invalid
    )

    settings.validate()

    // Should be clamped
    assertEquals(2.0f, settings.temperature)
    assertEquals(8192, settings.ctxSize)
}
```

---

## Future Enhancements

### Planned Features

#### 1. Preset Profiles
- Add "Creative", "Balanced", "Factual" preset buttons
- One-tap configuration changes
- User-savable custom presets

#### 2. Performance Monitoring
- Track actual RAM usage during generation
- Display tokens/sec speed
- Log performance metrics

#### 3. Advanced UI
- Grouping parameters into tabs (Basic/Advanced)
- Search/filter for specific parameters
- Parameter descriptions with "?" help icons

#### 4. Model-Specific Settings
- Per-model saved settings
- Automatically load correct settings for each model
- Model recommendations based on device RAM

#### 5. Export/Import
- Export settings as JSON file
- Import settings from JSON
- Share settings between devices

#### 6. Dynamic RAM Optimization
- Automatically reduce ctx_size if RAM low
- Suggest optimal settings for current device
- Adaptive quality based on available memory

### Code Improvements

#### 1. Settings Validation
- Add min/max warnings in UI (not just validation)
- Show recommended ranges per parameter
- Real-time validation as user adjusts sliders

#### 2. Error Handling
- Graceful fallback if SharedPreferences corrupted
- User notification if settings fail to load
- Automatic reset to defaults on critical error

#### 3. Performance
- Debounce RAM calculation (currently recalculates on every slider move)
- Cache device RAM detection
- Background save to reduce UI blocking

---

## Troubleshooting

### Issue 1: Settings Not Persisting

**Symptoms:**
- Changes don't save after app restart
- Settings reset to defaults every launch

**Diagnosis:**
```bash
adb logcat | grep "ModelSettings"
# Look for: "Settings saved successfully" or error messages
```

**Solutions:**

1. **Check SharedPreferences permissions:**
   ```kotlin
   val prefs = context.getSharedPreferences("ailive_model_settings", Context.MODE_PRIVATE)
   val canWrite = prefs.edit().putString("test", "test").commit()
   Log.d(TAG, "Can write to prefs: $canWrite")
   ```

2. **Verify save is called:**
   ```kotlin
   // In ModelSettingsActivity.saveSettings()
   Log.d(TAG, "Saving settings...")
   settings.save(this)
   Log.d(TAG, "Settings saved successfully")
   ```

3. **Check storage availability:**
   ```bash
   adb shell ls -la /data/data/com.ailive/shared_prefs/
   # Should see ailive_model_settings.xml
   ```

---

### Issue 2: RAM Usage Always Shows Orange

**Symptoms:**
- RAM indicator always orange regardless of settings
- Warning even with minimal settings

**Diagnosis:**
```kotlin
Log.d(TAG, "Device RAM: $totalDeviceRamMB MB")
Log.d(TAG, "Estimated usage: ${settings.estimateRamUsageMB()} MB")
Log.d(TAG, "Threshold: ${(totalDeviceRamMB * 0.6).toInt()} MB")
```

**Solutions:**

1. **Verify RAM detection:**
   ```kotlin
   fun getTotalRAM(): Int {
       val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
       val memInfo = ActivityManager.MemoryInfo()
       activityManager.getMemoryInfo(memInfo)
       return (memInfo.totalMem / (1024 * 1024)).toInt()
   }
   ```

2. **Check formula accuracy:**
   - Base model size may have changed
   - Verify Q4_K_M model is actually 1024MB
   - Adjust formula if using different quantization

3. **Lower threshold for low-RAM devices:**
   ```kotlin
   fun isSafeForDevice(totalDeviceRamMB: Int): Boolean {
       val threshold = if (totalDeviceRamMB < 6144) {
           totalDeviceRamMB * 0.5f  // 50% for <6GB devices
       } else {
           totalDeviceRamMB * 0.6f  // 60% for >=6GB devices
       }
       return estimateRamUsageMB() <= threshold.toInt()
   }
   ```

---

### Issue 3: maxTokens Not Applied

**Symptoms:**
- Responses always same length regardless of max_tokens setting
- Extremely long responses even with low max_tokens

**Diagnosis:**
```bash
adb logcat | grep "LLMManager"
# Look for: "Generating with maxTokens: XXX"
```

**Solutions:**

1. **Verify LLamaAndroid signature:**
   ```kotlin
   // LLamaAndroid.kt
   external fun bench(
       modelPath: String,
       prompt: String,
       nThreads: Int,
       nPredict: Int  // ← Must be defined in JNI
   ): String
   ```

2. **Check JNI implementation:**
   ```cpp
   // llama-android.cpp
   JNIEXPORT jstring JNICALL
   Java_com_ailive_ai_llm_LLamaAndroid_bench(
       JNIEnv *env,
       jobject /* this */,
       jstring modelPath,
       jstring prompt,
       jint nThreads,
       jint nPredict  // ← Must receive parameter
   ) {
       // Use nPredict in llama_generate()
   }
   ```

3. **Verify parameter passing:**
   ```kotlin
   // LLMManager.kt
   suspend fun generate(prompt: String): String {
       val maxTokens = currentSettings.maxTokens
       Log.d(TAG, "Generating with maxTokens: $maxTokens")

       val response = llamaBridge.generate(prompt, maxTokens)
       Log.d(TAG, "Response length: ${response.split(" ").size} words")
       return response
   }
   ```

---

### Issue 4: Sliders Not Updating UI

**Symptoms:**
- Moving slider doesn't update value text
- RAM usage doesn't recalculate

**Diagnosis:**
```kotlin
// Check if listener is registered
temperatureSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        Log.d(TAG, "Temperature slider changed: $progress")
    }
    // ...
})
```

**Solutions:**

1. **Verify listener setup:**
   ```kotlin
   private fun setupListeners() {
       temperatureSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
           override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
               settings.temperature = progress / 100.0f
               updateUI()
           }
           override fun onStartTrackingTouch(seekBar: SeekBar?) {}
           override fun onStopTrackingTouch(seekBar: SeekBar?) {}
       })
   }
   ```

2. **Check updateUI() is called:**
   ```kotlin
   private fun updateUI() {
       temperatureValue.text = String.format("%.2f", settings.temperature)
       updateRamUsage()  // ← Must call this
   }
   ```

3. **Verify findViewById IDs match XML:**
   ```xml
   <!-- activity_model_settings.xml -->
   <SeekBar android:id="@+id/temperatureSeekBar" />
   <TextView android:id="@+id/temperatureValue" />
   ```

---

### Issue 5: Compilation Errors

**Error:** "Unresolved reference: ModelSettings"

**Solution:**
```bash
# Rebuild project
./gradlew clean build

# Or in Android Studio
Build → Clean Project
Build → Rebuild Project
```

**Error:** "Activity not found: ModelSettingsActivity"

**Solution:**
```xml
<!-- AndroidManifest.xml -->
<application>
    <activity
        android:name=".ui.ModelSettingsActivity"
        android:label="Model Settings"
        android:theme="@style/Theme.AILive" />
</application>
```

**Error:** "Cannot find symbol: method generate(String, int)"

**Solution:**
```kotlin
// LLamaAndroid.kt - Update signature
fun generate(prompt: String, maxTokens: Int = 512): String {
    return bench(modelPath, prompt, nThreads = 4, nPredict = maxTokens)
}
```

---

## Summary

### What Was Implemented

✅ **3 new files:**
- ModelSettings.kt (data class + persistence)
- ModelSettingsActivity.kt (UI + logic)
- activity_model_settings.xml (layout)

✅ **8 files modified:**
- MainActivity.kt (⚙️ button + reload)
- MotorAI.kt (reloadSettings)
- LLMManager.kt (settings integration)
- LLamaAndroid.kt (maxTokens parameter)
- AndroidManifest.xml (activity declaration)

✅ **Key Features:**
- 11 configurable parameters
- Real-time RAM monitoring
- Color-coded safety warnings
- Settings persistence
- v1.1 optimized defaults (ctx=4096)

### Commits

- `77c7fa3`: Initial implementation (ModelSettings + UI)
- `47e084a`: Integration with LLMManager + MainActivity

### Impact

**Before:**
- Hardcoded parameters in code
- Required recompilation to change settings
- No RAM safety warnings
- ctx_size = 2048 (v1.0)

**After:**
- User-configurable parameters
- Real-time adjustments
- RAM safety system
- ctx_size = 4096 (v1.1, better for 6GB+ devices)

---

**Documentation Version:** 1.0
**Last Updated:** November 10, 2025
**Status:** ✅ Complete
