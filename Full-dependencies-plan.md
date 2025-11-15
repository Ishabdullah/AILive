# Full Dependencies Plan - AILive Project

## Overview
This document outlines all missing dependencies, incomplete implementations, and required fixes to resolve the remaining ~140+ Kotlin compilation errors in the AILive Android project.

---

## 1. Jetpack Compose Dependencies (MemoryActivity.kt)

### Issues
- Missing Compose UI libraries causing ~40+ unresolved references
- Errors: `Unresolved reference 'compose'`, `Unresolved reference 'Composable'`, etc.

### Files Affected
- `app/src/main/java/com/ailive/ui/MemoryActivity.kt`

### Solution
**Add to `app/build.gradle.kts`:**

```kotlin
dependencies {
    // Jetpack Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Material Design 3
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Activity Compose
    implementation("androidx.activity:activity-compose:1.8.2")

    // ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Compose Foundation
    implementation("androidx.compose.foundation:foundation")

    // Debug tooling
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

android {
    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}
```

**Priority:** HIGH (affects 1 file, ~40 errors)

---

## 2. Room Database DAO Implementation (LongTermMemoryManager.kt)

### Issues
- Missing DAO methods in `LongTermFactDao` interface
- Errors: `Unresolved reference 'searchFacts'`, `'insertFact'`, `'updateFact'`, etc.

### Files Affected
- `app/src/main/java/com/ailive/memory/managers/LongTermMemoryManager.kt`
- `app/src/main/java/com/ailive/memory/database/dao/LongTermFactDao.kt` (needs implementation)

### Solution
**Create/Update `LongTermFactDao.kt`:**

```kotlin
@Dao
interface LongTermFactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFact(fact: LongTermFactEntity): Long

    @Update
    suspend fun updateFact(fact: LongTermFactEntity)

    @Delete
    suspend fun deleteFact(fact: LongTermFactEntity)

    @Query("SELECT * FROM long_term_facts WHERE id = :factId")
    suspend fun getFact(factId: String): LongTermFactEntity?

    @Query("SELECT * FROM long_term_facts")
    suspend fun getAllFacts(): List<LongTermFactEntity>

    @Query("SELECT * FROM long_term_facts WHERE category = :category")
    suspend fun getFactsByCategory(category: String): List<LongTermFactEntity>

    @Query("SELECT * FROM long_term_facts WHERE category = :category")
    fun getFactsByCategoryFlow(category: String): Flow<List<LongTermFactEntity>>

    @Query("SELECT * FROM long_term_facts WHERE importance >= :minImportance")
    suspend fun getImportantFacts(minImportance: Int = 7): List<LongTermFactEntity>

    @Query("SELECT * FROM long_term_facts WHERE factText LIKE '%' || :query || '%'")
    suspend fun searchFacts(query: String): List<LongTermFactEntity>

    @Query("SELECT * FROM long_term_facts WHERE isVerified = 0")
    suspend fun getUnverifiedFacts(): List<LongTermFactEntity>

    @Query("DELETE FROM long_term_facts WHERE importance < :threshold AND createdAt < :cutoffTime")
    suspend fun deleteLowImportanceOldFacts(threshold: Int, cutoffTime: Long): Int

    @Query("SELECT COUNT(*) FROM long_term_facts")
    suspend fun getFactCount(): Int

    @Query("SELECT AVG(importance) FROM long_term_facts")
    suspend fun getAverageImportance(): Float

    @Query("SELECT category, COUNT(*) as count FROM long_term_facts GROUP BY category")
    suspend fun getFactCountByCategory(): Map<String, Int>
}
```

**Add extension methods to `LongTermFactEntity`:**

```kotlin
fun LongTermFactEntity.withAccessUpdate(): LongTermFactEntity {
    return this.copy(
        lastAccessedAt = System.currentTimeMillis(),
        accessCount = accessCount + 1
    )
}

fun LongTermFactEntity.withVerification(verified: Boolean): LongTermFactEntity {
    return this.copy(
        isVerified = verified,
        verificationCount = verificationCount + 1
    )
}
```

**Priority:** HIGH (affects 1 file, ~25 errors)

---

## 3. MainActivity Missing Properties and Methods

### Issues
- Missing properties: `modelManager`, `llmBridge`
- Missing methods: `onError`, `absolutePath`, `setImageBitmap`, `visibility`, `recycle`
- Type mismatches with Bitmap

### Files Affected
- `app/src/main/java/com/ailive/MainActivity.kt`

### Solution A: Add missing properties
```kotlin
class MainActivity : AppCompatActivity() {
    // ... existing properties ...

    private lateinit var modelManager: ModelManager
    private val llmBridge = LLMBridge()
}
```

### Solution B: Fix image handling
```kotlin
// In onCreate or appropriate location
imageViewCaptured.setOnClickListener {
    capturedImageBitmap?.let { bitmap ->
        imageViewCaptured.setImageBitmap(bitmap)
        imageViewCaptured.visibility = View.VISIBLE
    }
}

// Cleanup
override fun onDestroy() {
    super.onDestroy()
    capturedImageBitmap?.recycle()
    capturedImageBitmap = null
}
```

### Solution C: Add error handler
```kotlin
private fun onError(message: String) {
    runOnUiThread {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e(TAG, message)
    }
}
```

### Solution D: Fix file handling
```kotlin
// Replace absolutePath with proper file handling
val file = File(context.filesDir, "model.gguf")
val modelPath = file.absolutePath
```

**Priority:** MEDIUM (affects 1 file, ~15 errors)

---

## 4. Audio System Missing Methods (CommandRouter.kt, WakeWordDetector.kt)

### Issues
- Missing `speakAsAgent()` method in AudioManager or TTSManager
- Missing `playFeedback()` and `FeedbackType` enum
- Missing `HIGH` priority constant

### Files Affected
- `app/src/main/java/com/ailive/audio/CommandRouter.kt`
- `app/src/main/java/com/ailive/audio/WakeWordDetector.kt`
- `app/src/main/java/com/ailive/audio/AudioManager.kt` (needs additions)

### Solution A: Add TTS methods to AudioManager/TTSManager

```kotlin
// In TTSManager or AudioManager
enum class FeedbackType {
    WAKE_WORD_DETECTED,
    COMMAND_RECEIVED,
    ERROR,
    SUCCESS
}

suspend fun speakAsAgent(text: String, agentName: String = "AILive") {
    // Use existing TTS to speak with agent personality
    speak(text)
}

fun playFeedback(type: FeedbackType) {
    when (type) {
        FeedbackType.WAKE_WORD_DETECTED -> {
            // Play wake word sound or TTS confirmation
            lifecycleScope.launch {
                speak("Yes?")
            }
        }
        FeedbackType.COMMAND_RECEIVED -> {
            // Play acknowledgment sound
        }
        FeedbackType.ERROR -> {
            // Play error sound
        }
        FeedbackType.SUCCESS -> {
            // Play success sound
        }
    }
}
```

### Solution B: Add priority enum

```kotlin
enum class Priority {
    LOW, MEDIUM, HIGH
}

// Or use existing Android priority constants
import android.os.Process
// Then use Process.THREAD_PRIORITY_DEFAULT, etc.
```

**Priority:** HIGH (affects 2 files, ~10 errors)

---

## 5. AILiveCore Missing Priority Enum

### Issues
- Missing `LOW` priority constant

### Files Affected
- `app/src/main/java/com/ailive/core/AILiveCore.kt`

### Solution
```kotlin
enum class CorePriority {
    LOW, MEDIUM, HIGH
}

// Or import from audio if already defined there
```

**Priority:** LOW (affects 1 file, 1 error)

---

## 6. PersonalityEngine Missing TTS Properties

### Issues
- Missing `pitch` and `speechRate` properties

### Files Affected
- `app/src/main/java/com/ailive/personality/PersonalityEngine.kt`

### Solution
```kotlin
class PersonalityEngine(/* ... */) {
    // ... existing code ...

    // TTS configuration for personality
    var pitch: Float = 1.0f
        private set

    var speechRate: Float = 1.0f
        private set

    fun updateVoiceParameters(personality: AgentPersonality) {
        // Adjust TTS parameters based on personality
        pitch = when (personality) {
            AgentPersonality.PROFESSIONAL -> 1.0f
            AgentPersonality.FRIENDLY -> 1.1f
            AgentPersonality.TECHNICAL -> 0.95f
            // ... etc
        }

        speechRate = when (personality) {
            AgentPersonality.PROFESSIONAL -> 1.0f
            AgentPersonality.FRIENDLY -> 1.05f
            AgentPersonality.TECHNICAL -> 0.9f
            // ... etc
        }
    }
}
```

**Priority:** LOW (affects 1 file, 2 errors)

---

## 7. Type Inference Issues (LLMManager.kt)

### Issues
- Lambda type inference failures
- `StringBuilder.append()` overload ambiguity

### Files Affected
- `app/src/main/java/com/ailive/ai/llm/LLMManager.kt`

### Solution
```kotlin
// Fix type inference by adding explicit types
val someCallback: (String) -> Unit = { text ->
    // Process text
}

// Fix StringBuilder ambiguity by being explicit
val builder = StringBuilder()
builder.append(text.toString()) // Force String type
```

**Priority:** MEDIUM (affects 1 file, ~5 errors)

---

## 8. Missing ModelManager Integration

### Issues
- ModelManager class referenced but may not exist or be incomplete

### Files Affected
- `app/src/main/java/com/ailive/ai/models/ModelManager.kt`

### Solution
Create or verify ModelManager exists:

```kotlin
class ModelManager(private val context: Context) {
    fun isModelAvailable(modelName: String): Boolean {
        val file = File(context.filesDir, modelName)
        return file.exists() && file.length() > 0
    }

    fun getModelPath(modelName: String): String {
        return File(context.filesDir, modelName).absolutePath
    }

    // ... other model management methods
}
```

**Priority:** MEDIUM (affects multiple files)

---

## Implementation Order

### Phase 1: Critical Dependencies (Week 1)
1. ✅ Fix C++ API compatibility (COMPLETED)
2. ✅ Fix basic Kotlin imports (COMPLETED)
3. **Add Room DAO methods** (2-3 hours)
4. **Add audio system methods** (2-3 hours)
5. **Fix MainActivity properties** (1-2 hours)

### Phase 2: UI Dependencies (Week 1-2)
6. **Add Jetpack Compose dependencies** (30 minutes)
7. **Test Compose UI compilation** (1 hour)

### Phase 3: Refinements (Week 2)
8. **Fix type inference issues** (1-2 hours)
9. **Add personality TTS properties** (1 hour)
10. **Fix priority enums** (30 minutes)
11. **Complete ModelManager** (1-2 hours)

### Phase 4: Testing (Week 2-3)
12. **Build and test each component** (3-5 hours)
13. **Integration testing** (2-3 hours)
14. **Fix any remaining runtime issues** (variable)

---

## Gradle Configuration Summary

### Required `build.gradle.kts` additions:

```kotlin
android {
    namespace = "com.ailive"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        targetSdk = 34
    }

    buildFeatures {
        compose = true
        viewBinding = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // CameraX
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // Jetpack Compose (if using MemoryActivity)
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
}
```

---

## Testing Checklist

After implementing each phase:

- [ ] C++ compilation succeeds (no errors)
- [ ] Kotlin compilation succeeds (no errors)
- [ ] App builds successfully
- [ ] Room database queries work
- [ ] Audio TTS functionality works
- [ ] Camera integration works
- [ ] LLM model loads correctly
- [ ] Compose UI renders (if implemented)
- [ ] Memory management works
- [ ] No runtime crashes on startup

---

## Risk Assessment

### High Risk
- **Jetpack Compose** - May require significant refactoring if UI patterns don't match
- **Room DAO** - Schema changes may require database migrations

### Medium Risk
- **Audio methods** - May need to integrate with existing audio architecture
- **MainActivity refactoring** - Could affect other components

### Low Risk
- **Type inference fixes** - Usually straightforward
- **Property additions** - Minimal impact

---

## Notes

1. **Version Compatibility**: Ensure all library versions are compatible with:
   - Kotlin 1.9.x
   - Android Gradle Plugin 8.x
   - NDK 26.3.x

2. **Build Performance**: Large Compose dependency tree may increase build time

3. **APK Size**: Compose adds ~3-4MB to APK size

4. **Backwards Compatibility**: minSdk 26 chosen to support modern APIs while maintaining device coverage

5. **Testing Strategy**: Implement unit tests for Room DAO methods before integration

---

## Estimated Timeline

- **Total Compilation Fixes**: 15-20 hours
- **Testing & Debugging**: 5-10 hours
- **Documentation**: 2-3 hours
- **Total**: 22-33 hours (3-4 working days)

---

## Success Criteria

✅ Zero compilation errors
✅ All features compile without warnings
✅ App builds for all variants (debug, release)
✅ NDK build succeeds
✅ Basic runtime functionality verified
✅ No crashes on app startup
✅ LLM model can be loaded and used
✅ Audio input/output works
✅ Camera can capture frames

---

## Maintenance Plan

After initial fixes:

1. **Weekly**: Review new compilation warnings
2. **Monthly**: Update dependencies to latest stable versions
3. **Quarterly**: Review deprecated API usage
4. **As needed**: Fix new Kotlin/Android API changes

---

*Document Version: 1.0*
*Last Updated: 2025-11-15*
*Author: Claude (Sonnet 4.5)*
