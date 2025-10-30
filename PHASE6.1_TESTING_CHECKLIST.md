# Phase 6.1 Dashboard - User Testing Checklist

**Date:** October 30, 2025
**Phase:** 6.1 - Core Dashboard
**Tester:** User
**APK Version:** From GitHub Actions build

---

## 📱 Pre-Test Setup

### 1. Install APK
```bash
# Download from GitHub Actions artifacts
# Or use: gh run download <run-id>

# Install on device
adb install -r app-debug.apk
```

### 2. Launch App
- Open AILive from app drawer
- Grant camera and microphone permissions when prompted
- Wait for "ANALYZING..." status to appear
- Confirm camera view is working

---

## 🎯 Dashboard Access Tests

### Test 1: Dashboard Toggle Button
**Goal:** Verify dashboard can be opened and closed

**Steps:**
1. Look for orange FAB (Floating Action Button) in **top-right corner**
2. Tap the FAB button once
3. Dashboard should overlay the camera view
4. Tap FAB button again
5. Dashboard should close, camera view visible again

**Expected Results:**
- ✅ FAB button visible in top-right
- ✅ Dashboard opens on first tap
- ✅ Dashboard shows dark background (#1E1E1E)
- ✅ Dashboard closes on second tap
- ✅ Camera view accessible when dashboard closed

**Report Issues:**
- [ ] FAB button not visible
- [ ] Dashboard doesn't open
- [ ] Dashboard doesn't close
- [ ] UI glitches or crashes
- [ ] Other: _______________

---

## 📊 Dashboard Display Tests

### Test 2: Statistics Card
**Goal:** Verify statistics display correctly

**Steps:**
1. Open dashboard (tap FAB)
2. Look at top statistics card
3. Check all 4 statistics displayed

**Expected Results:**
- ✅ "Total Tools" shows: **6**
- ✅ "Active" shows: **6** (in green)
- ✅ "Executions" shows: **0** initially (in blue)
- ✅ "Success" shows: **--** initially (in green)

**Report Issues:**
- [ ] Statistics card missing
- [ ] Wrong values displayed
- [ ] Colors incorrect
- [ ] Text not readable
- [ ] Other: _______________

---

### Test 3: Tool Status Cards
**Goal:** Verify all 6 tools are displayed

**Steps:**
1. Open dashboard
2. Scroll through tool cards
3. Verify each tool is present

**Expected Tool Cards (in order):**
1. 🎭 **Sentiment Analysis** (`analyze_sentiment`)
2. 🎮 **Device Control** (`control_device`)
3. 💾 **Memory Retrieval** (`retrieve_memory`)
4. 👁️ **Vision Analysis** (`analyze_vision`)
5. 📊 **Pattern Analysis** (`analyze_patterns`)
6. ⭐ **Feedback Tracking** (`track_feedback`)

**Expected for Each Card:**
- Tool icon (emoji)
- Display name (e.g., "Sentiment Analysis")
- Internal name (e.g., "analyze_sentiment")
- Status badge (should show "● Ready" in green initially)
- Execution stats (should show "0 executions • Last: never")

**Report Issues:**
- [ ] Missing tool cards (specify which): _______________
- [ ] Wrong icons
- [ ] Wrong names
- [ ] Status not showing
- [ ] Execution stats not showing
- [ ] Cards not scrollable
- [ ] Other: _______________

---

## 🔄 Real-Time Update Tests

### Test 4: Tool Execution Updates
**Goal:** Verify dashboard updates when tools execute

**Steps:**
1. Open dashboard
2. Keep dashboard visible
3. Type or say a command that triggers tools:
   - Try: "What do you see?" (triggers Vision)
   - Try: "How are you feeling?" (triggers Sentiment)
   - Try: "Turn on flashlight" (triggers Device Control)
4. Watch the corresponding tool card
5. Wait 2 seconds after execution

**Expected Results:**
- ✅ Tool status changes from "● Ready" (green) to "● Executing" (blue)
- ✅ Then changes to "● Success" (green) or "● Error" (red)
- ✅ Execution count increments by 1
- ✅ "Last: Xs ago" updates to show recent time
- ✅ After 2 seconds, status returns to "● Ready" (green)

**Report Issues:**
- [ ] Status doesn't change during execution
- [ ] Execution count doesn't increment
- [ ] Last execution time doesn't update
- [ ] Status doesn't return to Ready
- [ ] Wrong colors displayed
- [ ] Dashboard freezes
- [ ] Other: _______________

---

### Test 5: Statistics Updates
**Goal:** Verify statistics update with tool executions

**Steps:**
1. Note initial statistics values
2. Execute 3-5 different commands
3. Check statistics after each command

**Expected Results:**
- ✅ "Executions" count increases with each tool execution
- ✅ "Success" percentage appears after first execution
- ✅ Success rate shows as percentage (e.g., "100%" or "80%")
- ✅ All values update automatically (no manual refresh needed)

**Report Issues:**
- [ ] Execution count doesn't increase
- [ ] Success rate doesn't calculate
- [ ] Success rate shows wrong value
- [ ] Statistics don't auto-update
- [ ] Other: _______________

---

### Test 6: Auto-Refresh
**Goal:** Verify dashboard refreshes every 2 seconds

**Steps:**
1. Open dashboard
2. Execute a command
3. Watch the "Last: Xs ago" on tool cards
4. Leave dashboard open for 30 seconds

**Expected Results:**
- ✅ Time updates from "1s ago" → "2s ago" → "3s ago" etc.
- ✅ Updates happen smoothly without jank
- ✅ No app slowdown with dashboard open
- ✅ UI remains responsive

**Report Issues:**
- [ ] Time doesn't update
- [ ] Updates are jumpy/laggy
- [ ] App slows down
- [ ] Dashboard freezes
- [ ] Other: _______________

---

## 🎨 UI/UX Quality Tests

### Test 7: Visual Design
**Goal:** Check overall look and feel

**Checklist:**
- [ ] Dark theme looks good (dark gray background)
- [ ] Cards have rounded corners
- [ ] Text is readable on dark background
- [ ] Colors are vibrant and distinct
- [ ] Icons (emojis) display correctly
- [ ] No layout overlap or clipping
- [ ] Spacing looks balanced

**Report Issues:**
- [ ] Colors look wrong
- [ ] Text hard to read
- [ ] Layout broken on my device
- [ ] Icons don't display
- [ ] UI looks cluttered
- [ ] Other: _______________

---

### Test 8: Scrolling & Performance
**Goal:** Test smooth scrolling and performance

**Steps:**
1. Open dashboard
2. Scroll up and down through tool cards
3. Toggle dashboard open/close multiple times
4. Execute several commands with dashboard open

**Expected Results:**
- ✅ Scrolling is smooth
- ✅ No lag when opening/closing dashboard
- ✅ Dashboard doesn't slow down app
- ✅ No crashes or freezes

**Report Issues:**
- [ ] Scrolling is laggy
- [ ] Dashboard slow to open
- [ ] App freezes with dashboard
- [ ] Crashes occur
- [ ] Battery drains quickly
- [ ] Other: _______________

---

## 🐛 Edge Case Tests

### Test 9: Rapid Commands
**Goal:** Test dashboard with rapid tool execution

**Steps:**
1. Open dashboard
2. Send 5-10 commands quickly in succession
3. Watch tool cards update

**Expected Results:**
- ✅ All executions tracked correctly
- ✅ No missed updates
- ✅ Statistics remain accurate
- ✅ No UI glitches

**Report Issues:**
- [ ] Missed executions
- [ ] Wrong counts
- [ ] UI breaks with rapid updates
- [ ] App crashes
- [ ] Other: _______________

---

### Test 10: Dashboard Lifecycle
**Goal:** Test dashboard across app lifecycle events

**Steps:**
1. Open dashboard
2. Press home button (app goes to background)
3. Return to app
4. Close and reopen dashboard
5. Execute a command
6. Rotate device (if applicable)

**Expected Results:**
- ✅ Dashboard state maintained when returning from background
- ✅ Statistics preserved
- ✅ Dashboard works normally after reopen
- ✅ No crashes on rotation
- ✅ UI adjusts to orientation (if applicable)

**Report Issues:**
- [ ] Dashboard breaks after background
- [ ] Statistics reset incorrectly
- [ ] Crashes on reopen
- [ ] Rotation breaks layout
- [ ] Other: _______________

---

## 📝 Test Results Template

After completing all tests, please provide results in this format:

```
## Phase 6.1 Dashboard Test Results

**Device:** [Your device model]
**Android Version:** [e.g., Android 13]
**APK Build:** [GitHub Actions run number]
**Test Date:** [Date]

### ✅ Passed Tests:
- Test 1: Dashboard Toggle - PASS
- Test 2: Statistics Card - PASS
- [etc.]

### ❌ Failed Tests:
- Test X: [Name] - FAIL
  - Issue: [Describe what went wrong]
  - Screenshots: [If available]

### 🐛 Bugs Found:
1. [Description of bug]
2. [Description of bug]

### 💡 Suggestions:
- [Any UX improvements]
- [Feature requests]

### Overall Rating:
[ ] Excellent - Works perfectly
[ ] Good - Minor issues
[ ] Fair - Several issues
[ ] Poor - Major problems
```

---

## 🚀 Quick Test (5 Minutes)

If you only have 5 minutes, run this quick test:

1. ✅ Open app, tap FAB in top-right
2. ✅ Verify 6 tool cards displayed
3. ✅ Send command: "What do you see?"
4. ✅ Watch Vision Analysis card update
5. ✅ Check execution count increased
6. ✅ Close dashboard, reopen
7. ✅ Verify statistics maintained

**Result:** [ ] Quick test passed / [ ] Issues found

---

## 📞 Reporting Issues

When reporting issues, please include:

1. **What you did** (exact steps)
2. **What you expected** (should happen)
3. **What happened** (actually happened)
4. **Screenshots** (if relevant)
5. **Device info** (model, Android version)

**Example:**
```
Test 4 Failed:
- Steps: Opened dashboard, said "What do you see?"
- Expected: Vision Analysis card shows "● Executing" in blue
- Actual: Card stayed at "● Ready" in green, didn't update
- Device: Samsung Galaxy S21, Android 13
- Screenshot: [attached]
```

---

## ✅ Success Criteria

Phase 6.1 dashboard is considered **successful** if:

- [ ] All 6 tool cards display correctly
- [ ] Dashboard opens and closes smoothly
- [ ] Tool status updates in real-time
- [ ] Statistics calculate correctly
- [ ] No crashes or major bugs
- [ ] UI is readable and attractive
- [ ] Performance is acceptable

---

**Happy Testing! 🎉**

Report your results and I'll help fix any issues or make improvements!
