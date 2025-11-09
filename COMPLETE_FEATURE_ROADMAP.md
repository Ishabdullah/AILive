# AILive Complete Feature Roadmap

## Feature Extraction & Organization

### Extracted from Requirements

#### Core AI Capabilities
1. ✅ **Text-based chat** (Current - Working)
2. 🔄 **Vision support** (Waiting for upstream - Option A)
3. ⏳ **Temporal understanding** (time/date awareness)
4. 📍 **GPS/Location awareness**
5. 🌐 **Web search capabilities** (with source integration: weather, Reddit, Wikipedia, etc.)
6. 🧠 **Persistent memory system** (working, short-term, long-term)
7. 👤 **User-specific memory** (personal info, preferences, relationships)
8. 📚 **Learning system** (mini training sessions, feedback-based learning)
9. 🔍 **Autonomous research** (nighttime background research)

#### User Experience
10. ✏️ **Custom AI name** (first-time setup)
11. 📊 **Working statistics** (real-time updates in settings)
12. 🎨 **Streaming token display** (ChatGPT-style)
13. 🪟 **Display over other apps** (overlay mode)
14. 👁️ **Screen visibility** (see what user sees)
15. 💬 **Screen commenting** (contextual observations)
16. 🌐 **Browser mode** (when camera off, Comet-style browsing)

#### Phone Control Integration
17. 📱 **Message control** (send/read SMS)
18. ☎️ **Phone call control**
19. ⏰ **Alarm scheduling**
20. 📅 **Calendar management**
21. 🔦 **Flashlight control**
22. 🔊 **Volume control**
23. 📷 **Camera control**
24. 🔊 **Audio control**
25. 🤖 **Screen macros** (automate screen interactions)

#### Advanced Learning
26. ✅/❌ **Feedback system** (correct/incorrect with explanations)
27. 🔄 **Claim verification** (research to support/refute)
28. 📖 **Training data generation** (from user feedback)
29. 🎯 **Model fine-tuning** (on-device learning)

---

## Organized by Difficulty Level

### 🟢 **EASY** (1-3 days each)

#### 1. Temporal Understanding ⏱️
**Current State**: None
**What's Needed**:
- Add system time/date to prompt context
- Parse user queries for time-based requests
- Format responses with temporal awareness

**Implementation**:
```kotlin
// Add to LLMManager.kt
private fun createChatPrompt(userMessage: String, agentName: String): String {
    val now = LocalDateTime.now()
    val dateTime = now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a"))

    return buildString {
        append("<|im_start|>system\n")
        append("You are $agentName, a helpful AI assistant.\n")
        append("Current date and time: $dateTime\n")  // ADD THIS
        append("<|im_end|>\n")
        // ... rest
    }
}
```

**Complexity**: ⭐☆☆☆☆ (Very Easy)
**Time**: 4-6 hours

---

#### 2. Custom AI Name ✏️
**Current State**: Hardcoded as "AILive"
**What's Needed**:
- First-run dialog to get name
- Store in SharedPreferences
- Use throughout app

**Implementation**:
```kotlin
// Add to MainActivity.kt
private fun showFirstRunNameDialog() {
    val input = EditText(this)
    AlertDialog.Builder(this)
        .setTitle("Welcome! What should I call myself?")
        .setView(input)
        .setPositiveButton("Continue") { _, _ ->
            val name = input.text.toString().ifEmpty { "AILive" }
            PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putString("ai_name", name)
                .apply()
        }
        .show()
}
```

**Complexity**: ⭐☆☆☆☆ (Very Easy)
**Time**: 2-4 hours

---

#### 3. Working Statistics 📊
**Current State**: Placeholder UI
**What's Needed**:
- Track metrics in real-time
- Store in database (Room)
- Update UI from database

**Metrics to Track**:
- Total conversations
- Total messages sent/received
- Total tokens generated
- Average response time
- Model load time
- Active time
- Memory usage

**Implementation**:
```kotlin
// Create StatisticsRepository.kt
@Dao
interface StatisticsDao {
    @Query("SELECT COUNT(*) FROM conversations")
    fun getTotalConversations(): LiveData<Int>

    @Query("SELECT SUM(token_count) FROM messages")
    fun getTotalTokens(): LiveData<Long>

    // etc.
}
```

**Complexity**: ⭐⭐☆☆☆ (Easy)
**Time**: 1 day

---

#### 4. GPS/Location Awareness 📍
**Current State**: None
**What's Needed**:
- Request location permissions
- Use FusedLocationProviderClient
- Add location to prompt context
- Reverse geocoding for city/address

**Implementation**:
```kotlin
// Add to AILiveCore.kt
private val fusedLocationClient: FusedLocationProviderClient by lazy {
    LocationServices.getFusedLocationProviderClient(context)
}

fun getCurrentLocation(): Location? {
    // Get last known location
    // Add to system prompt
}

// Prompt becomes:
// "Current location: New York, NY, USA"
// "Current coordinates: 40.7128° N, 74.0060° W"
```

**Complexity**: ⭐⭐☆☆☆ (Easy)
**Time**: 4-8 hours

---

### 🟡 **MEDIUM** (3-7 days each)

#### 5. Web Search Capabilities 🌐
**Current State**: None
**What's Needed**:
- Detect when search is needed (query intent)
- Use free search APIs (DuckDuckGo, SearXNG)
- Parse and summarize results
- Integrate into prompt context
- Source-specific searches (weather, Reddit, Wikipedia)

**APIs to Use** (All Free):
- **DuckDuckGo Instant Answer API**: https://api.duckduckgo.com/
- **Wikipedia API**: https://en.wikipedia.org/w/api.php
- **Weather**: OpenWeatherMap free tier or wttr.in
- **Reddit**: Old Reddit JSON (no auth needed for read)

**Implementation**:
```kotlin
// Create WebSearchManager.kt
class WebSearchManager {
    suspend fun search(query: String): SearchResult {
        // 1. Detect intent (weather? wikipedia? general?)
        // 2. Use appropriate API
        // 3. Parse and format results
        // 4. Return structured data
    }

    suspend fun shouldSearch(userMessage: String, aiKnowledge: String): Boolean {
        // Heuristics:
        // - Contains "search for", "look up", "what is current"
        // - Asks about recent events (beyond training date)
        // - Weather-related queries
        // - Specific facts that need verification
    }
}

// Integration:
// Before generating response, check if search needed
// If yes, perform search and add results to context
```

**Complexity**: ⭐⭐⭐☆☆ (Medium)
**Time**: 3-5 days

---

#### 6. Persistent Memory System 🧠
**Current State**: Conversation history in memory only
**What's Needed**:
- **Working Memory**: Current conversation (already have)
- **Short-term Memory**: Recent conversations (last 24h-7days)
- **Long-term Memory**: Important facts, user preferences
- Vector database for semantic search (or SQLite with embeddings)
- Memory retrieval based on relevance

**Implementation**:
```kotlin
// Memory hierarchy:
data class Memory(
    val id: String,
    val content: String,
    val timestamp: Long,
    val importance: Float,  // 0.0 to 1.0
    val memoryType: MemoryType,
    val embedding: FloatArray?  // Optional: for semantic search
)

enum class MemoryType {
    WORKING,      // Current conversation
    SHORT_TERM,   // Last 7 days
    LONG_TERM,    // Important facts to remember
    USER_INFO     // Specific to user
}

class MemoryManager {
    // Store memories in SQLite
    // Retrieve relevant memories for context
    // Prune old short-term memories
    // Promote important short-term → long-term
}
```

**Complexity**: ⭐⭐⭐☆☆ (Medium)
**Time**: 5-7 days

---

#### 7. User-Specific Memory 👤
**Current State**: None
**What's Needed**:
- Extract user info from conversations
- Categorize (name, birthdate, preferences, relationships)
- Store securely (encrypted SharedPreferences or Room)
- Retrieve for personalization

**Categories**:
- Personal: Name, birthdate, location, occupation
- Preferences: Favorite color, sports teams, interests
- Relationships: Family, friends (names, birthdays, info)
- Goals: User's objectives, projects, aspirations

**Implementation**:
```kotlin
data class UserInfo(
    val name: String? = null,
    val birthday: String? = null,
    val location: String? = null,
    val favoriteColor: String? = null,
    val favoriteSportsTeams: List<String> = emptyList(),
    val interests: List<String> = emptyList()
)

data class Relationship(
    val name: String,
    val relation: String,  // "mother", "friend", "colleague"
    val birthday: String? = null,
    val notes: String
)

// Extract from conversation:
// "My birthday is March 15th" → parse and store
// "My mom's name is Susan" → create Relationship
```

**Complexity**: ⭐⭐⭐☆☆ (Medium)
**Time**: 4-6 days

---

#### 8. Display Over Other Apps 🪟
**Current State**: Standard activity
**What's Needed**:
- Request SYSTEM_ALERT_WINDOW permission
- Create overlay service
- Floating bubble UI (like Messenger)
- Expand to full chat when tapped

**Implementation**:
```kotlin
// Create OverlayService.kt
class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Create floating bubble
        // Position on screen
        // Handle touch events
    }
}

// Permissions:
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

**Complexity**: ⭐⭐⭐☆☆ (Medium)
**Time**: 3-5 days

---

#### 9. Streaming Token Display 💬
**Current State**: Shows full response at once
**What's Needed**:
- Already have Flow API from llama.cpp
- Update UI to display tokens incrementally
- Smooth scrolling
- Typing indicator

**Implementation**:
```kotlin
// Update ChatUI.kt
suspend fun streamResponse(prompt: String) {
    val messageIndex = addPendingMessage("Thinking...")
    val response = StringBuilder()

    llmManager.generate(prompt).collect { token ->
        response.append(token)
        updateMessage(messageIndex, response.toString())
        recyclerView.smoothScrollToPosition(messageIndex)
        delay(10)  // Smooth animation
    }

    markMessageComplete(messageIndex)
}
```

**Complexity**: ⭐⭐☆☆☆ (Easy-Medium)
**Time**: 1-2 days

---

### 🟠 **HARD** (1-2 weeks each)

#### 10. Screen Visibility & Commenting 👁️
**Current State**: No screen access
**What's Needed**:
- MediaProjection API for screenshots
- Request SCREEN_CAPTURE permission
- Capture screen periodically or on-demand
- Send to vision model (when available)
- Generate contextual comments

**Implementation**:
```kotlin
// Create ScreenCaptureManager.kt
class ScreenCaptureManager(private val context: Context) {
    private lateinit var mediaProjection: MediaProjection

    fun captureScreen(): Bitmap {
        // Use MediaProjection to get screen contents
        // Return as Bitmap
    }
}

// Usage:
// User asks: "What am I looking at?"
// Capture screen → Send to vision model → Generate description
```

**Complexity**: ⭐⭐⭐⭐☆ (Hard)
**Time**: 7-10 days
**Blocker**: Requires vision support (Option A - waiting for upstream)

---

#### 11. Phone Control Integration 📱
**Current State**: None
**What's Needed**:
- Multiple Android permissions
- APIs for each function
- Intent detection in user messages
- Confirmation dialogs for sensitive actions

**Sub-features**:

**Messages (SMS)**:
```kotlin
// Read messages
val cursor = context.contentResolver.query(Uri.parse("content://sms/inbox"), ...)

// Send message
SmsManager.getDefault().sendTextMessage(phoneNumber, null, message, null, null)
```

**Phone Calls**:
```kotlin
val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber"))
context.startActivity(intent)
```

**Alarms**:
```kotlin
val alarmManager = context.getSystemService(AlarmManager::class.java)
alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
```

**Calendar**:
```kotlin
val intent = Intent(Intent.ACTION_INSERT).apply {
    data = CalendarContract.Events.CONTENT_URI
    putExtra(CalendarContract.Events.TITLE, title)
    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
}
```

**Flashlight/Volume/Camera/Audio**:
```kotlin
// Flashlight
val cameraManager = context.getSystemService(CameraManager::class.java)
cameraManager.setTorchMode(cameraId, true)

// Volume
val audioManager = context.getSystemService(AudioManager::class.java)
audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
```

**Permissions Needed**:
```xml
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.SEND_SMS" />
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.READ_CALENDAR" />
<uses-permission android:name="android.permission.WRITE_CALENDAR" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

**Complexity**: ⭐⭐⭐⭐☆ (Hard)
**Time**: 10-14 days (for all sub-features)

---

#### 12. Screen Macros/Control 🤖
**Current State**: None
**What's Needed**:
- AccessibilityService for UI automation
- Parse user intent to actions
- Simulate touches, swipes, text input
- Complex: Requires AI to understand UI structure

**Implementation**:
```kotlin
// Create AILiveAccessibilityService.kt
class AILiveAccessibilityService : AccessibilityService() {
    fun performClick(x: Int, y: Int) {
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        dispatchGesture(gesture, null, null)
    }

    fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int) {
        // Similar with path from start to end
    }

    fun typeText(text: String) {
        // Use ACTION_SET_TEXT or input events
    }
}
```

**Complexity**: ⭐⭐⭐⭐⭐ (Very Hard)
**Time**: 14-21 days
**Challenge**: Understanding UI context and user intent

---

#### 13. Browser Mode 🌐
**Current State**: None
**What's Needed**:
- WebView integration
- AI-controlled browsing (similar to Comet browser)
- Extract page content
- Summarize and interact
- Only active when camera is off

**Implementation**:
```kotlin
// Create BrowserFragment.kt
class BrowserFragment : Fragment() {
    private lateinit var webView: WebView

    fun navigateTo(url: String) {
        webView.loadUrl(url)
    }

    fun extractPageContent(): String {
        // JavaScript injection to get text
        webView.evaluateJavascript(
            "(function() { return document.body.innerText; })();"
        ) { content ->
            // Send to AI for summarization
        }
    }
}

// Toggle in MainActivity:
// if (cameraEnabled) show chat UI
// else show browser UI
```

**Complexity**: ⭐⭐⭐⭐☆ (Hard)
**Time**: 7-14 days

---

### 🔴 **VERY HARD** (2-4 weeks+ each)

#### 14. Feedback & Training System ✅❌
**Current State**: None
**What's Needed**:
- Capture user feedback (correct/incorrect + explanation)
- Store as training examples
- Generate preference data
- *Potential*: Fine-tune model (very complex)

**Implementation**:
```kotlin
data class FeedbackExample(
    val userMessage: String,
    val aiResponse: String,
    val isCorrect: Boolean,
    val userExplanation: String,
    val timestamp: Long
)

class FeedbackManager {
    fun recordFeedback(
        message: String,
        response: String,
        correct: Boolean,
        explanation: String
    ) {
        // Store in database
        // Format as training example
        // Accumulate for future fine-tuning
    }
}

// UI:
// After each AI response, show buttons:
// ✅ Correct | ❌ Incorrect
// If incorrect, show dialog to explain why
```

**Complexity**: ⭐⭐⭐☆☆ (Medium for collection)
**Time**: 5-7 days for feedback collection
**Note**: Actual training is separate (see #16)

---

#### 15. Autonomous Research 🔍
**Current State**: None
**What's Needed**:
- Background service
- Runs at night (e.g., 2-6 AM)
- Identify topics based on user interests
- Search and store information
- Present findings later

**Implementation**:
```kotlin
// Create ResearchService.kt
class ResearchService : Service() {
    fun scheduleNightlyResearch() {
        // Use WorkManager for periodic task
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)  // Only when charging
            .setRequiresBatteryNotLow(true)
            .build()

        val work = PeriodicWorkRequestBuilder<ResearchWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
    }
}

class ResearchWorker : CoroutineWorker() {
    override suspend fun doWork(): Result {
        // 1. Get user interests from memory
        // 2. Generate research topics
        // 3. Search web for each topic
        // 4. Summarize findings
        // 5. Store in memory
        // 6. Present next day: "I researched X for you..."
    }
}
```

**Complexity**: ⭐⭐⭐⭐☆ (Hard)
**Time**: 10-14 days

---

#### 16. On-Device Fine-Tuning 🎯
**Current State**: None
**What's Needed**:
- **VERY COMPLEX**: Requires LoRA or similar
- Training loop implementation
- Convert feedback to training format
- Run gradient descent on-device
- Merge LoRA weights back to model

**Reality Check**:
- llama.cpp **does not support training** currently
- Would need separate training framework (e.g., MLX, llama.cpp fork)
- Very resource-intensive (hours of compute)
- May not be feasible on mobile

**Possible Alternatives**:
1. **Cloud-based fine-tuning**: Send feedback data to server, train there
2. **Prompt engineering**: Use few-shot examples from feedback
3. **RAG**: Store feedback as context, retrieve relevant examples
4. **Wait for future tech**: Mobile training frameworks improving

**Complexity**: ⭐⭐⭐⭐⭐ (Extremely Hard)
**Time**: 30+ days (research + implementation)
**Recommendation**: Use alternative approaches for now

---

## Organized by Priority & Dependencies

### Phase 1: Foundation (Weeks 1-2)
1. ✅ Custom AI Name (4h)
2. ✅ Temporal Understanding (6h)
3. ✅ GPS/Location (8h)
4. ✅ Working Statistics (1d)
5. ✅ Streaming Display (2d)

**Total**: ~2 weeks

---

### Phase 2: Enhanced Capabilities (Weeks 3-6)
6. ✅ Web Search Integration (5d)
7. ✅ Persistent Memory System (7d)
8. ✅ User-Specific Memory (6d)
9. ✅ Display Over Apps (5d)

**Total**: ~4 weeks

---

### Phase 3: Phone Integration (Weeks 7-9)
10. ✅ Message Control (3d)
11. ✅ Phone Call Control (2d)
12. ✅ Alarm Scheduling (2d)
13. ✅ Calendar Management (3d)
14. ✅ Device Controls (flashlight, volume, etc.) (2d)

**Total**: ~2-3 weeks

---

### Phase 4: Advanced Features (Weeks 10-14)
15. ✅ Browser Mode (10d)
16. ✅ Feedback Collection System (7d)
17. ✅ Autonomous Research (14d)

**Total**: ~4-5 weeks

---

### Phase 5: Vision-Dependent (Waiting on Upstream)
18. ⏳ Screen Visibility (7d) - **Blocked by vision support**
19. ⏳ Screen Commenting (included in above)
20. ⏳ Screen Macros (21d) - **Very complex**

**Total**: ~4 weeks after vision available

---

### Phase 6: Learning (Long-term)
21. ⏳ Mini Training Sessions (14d) - **Research needed**
22. ⏳ On-Device Fine-Tuning (30d+) - **May not be feasible**

**Total**: 6+ weeks (research-dependent)

---

## Summary Statistics

**Total Features**: 22
**Easy**: 4 features (~1 week)
**Medium**: 5 features (~4 weeks)
**Hard**: 9 features (~10 weeks)
**Very Hard**: 4 features (~8+ weeks)

**Realistic Timeline**: 6-9 months for full implementation
**MVP Timeline**: 2-3 months (Foundation + Enhanced + Phone Integration)

---

## Technical Blockers

### 🚧 Vision Support
**Affects**: Screen visibility, screen commenting, visual macros
**Status**: Waiting for upstream llama.cpp Android
**Workaround**: HTTP API (Option C) for interim testing

### 🚧 On-Device Training
**Affects**: True learning, fine-tuning
**Status**: Not supported by llama.cpp
**Workaround**: Prompt engineering, RAG, few-shot learning

### 🚧 Battery/Performance
**Affects**: Autonomous research, continuous monitoring
**Status**: Need careful optimization
**Workaround**: Only run when charging, use WorkManager constraints

---

## Recommended Implementation Order

**Month 1**: Foundation (Easy wins, immediate value)
- Custom name
- Temporal understanding
- GPS
- Statistics
- Streaming display

**Month 2**: Enhanced Capabilities (Core AI improvements)
- Web search
- Persistent memory
- User memory
- Overlay mode

**Month 3**: Phone Integration (Practical utility)
- Messages
- Calls
- Alarms
- Calendar
- Device controls

**Month 4**: Advanced Features (Power user features)
- Browser mode
- Feedback system
- Autonomous research

**Month 5-6**: Vision & Learning (Once unblocked)
- Screen visibility (when vision available)
- Training experiments
- Screen macros

---

## Next Steps

1. Review this roadmap
2. Confirm priority order
3. Choose starting point
4. Create detailed todo list for Phase 1
5. Begin implementation

**Recommended Start**: Phase 1, Feature #1 (Custom AI Name) - 4 hour quick win!

What do you think? Should we proceed with this order, or would you like to adjust priorities?
