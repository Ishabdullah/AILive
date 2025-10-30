# Download & Test AILive Phase 6.1 Dashboard

**Build Status:** ✅ Running on GitHub Actions
**Expected Time:** 3-5 minutes

---

## 📥 Download APK (Once Build Completes)

### Option 1: GitHub Web Interface
1. Go to: https://github.com/Ishabdullah/AILive/actions
2. Click on the latest "Android CI" workflow (should be green ✅)
3. Scroll down to "Artifacts" section
4. Download `ailive-debug` or `ailive-release`
5. Extract the ZIP file
6. You'll find `app-debug.apk` or `app-release.apk`

### Option 2: Command Line (if you have gh CLI)
```bash
# Check build status
gh run list --limit 1

# Once completed, download artifacts
gh run download $(gh run list --limit 1 --json databaseId -q '.[0].databaseId')

# APK will be in downloaded folder
ls ailive-debug/app-debug.apk
```

---

## 📱 Install APK on Your Device

### Method 1: ADB (if device connected)
```bash
adb install -r ailive-debug/app-debug.apk
```

### Method 2: Manual Transfer
1. Transfer APK to your phone (via USB, cloud, etc.)
2. Open file manager on phone
3. Tap the APK file
4. Allow "Install from unknown sources" if prompted
5. Tap "Install"

---

## 🧪 Run Tests

Once installed, follow the comprehensive test checklist in:
**`PHASE6.1_TESTING_CHECKLIST.md`**

### Quick 5-Minute Test
If you're short on time, run this quick test:

1. ✅ **Open AILive app**
2. ✅ **Tap FAB button** (orange circle, top-right corner)
3. ✅ **Verify dashboard opens** with dark background
4. ✅ **Check all 6 tool cards** are visible:
   - 🎭 Sentiment Analysis
   - 🎮 Device Control
   - 💾 Memory Retrieval
   - 👁️ Vision Analysis
   - 📊 Pattern Analysis
   - ⭐ Feedback Tracking
5. ✅ **Send a command**: "What do you see?"
6. ✅ **Watch Vision Analysis card** - should show:
   - Status changes to "● Executing" (blue)
   - Then "● Success" (green)
   - Execution count increases to 1
   - Shows "Last: 0s ago"
7. ✅ **Check statistics card** at top:
   - Executions: 1
   - Success: 100%
8. ✅ **Close and reopen dashboard** - statistics should persist

**Result:** [ ] Quick test passed / [ ] Issues found

---

## 📝 Report Results

Please provide:

1. **Build Status**
   - [ ] APK downloaded successfully
   - [ ] APK installed successfully
   - [ ] App launches without crashes

2. **Dashboard Test**
   - [ ] Dashboard opens/closes correctly
   - [ ] All 6 tools displayed
   - [ ] Real-time updates working
   - [ ] Statistics calculate correctly

3. **Any Issues** (if found):
   ```
   Issue: [Description]
   Steps to reproduce: [1, 2, 3...]
   Expected: [What should happen]
   Actual: [What happened]
   ```

4. **Screenshots** (if applicable):
   - Dashboard open
   - Tool cards
   - Any errors

---

## 🐛 Common Issues & Solutions

### Issue: "App not installed"
**Solution:** Uninstall old AILive version first, then reinstall

### Issue: Dashboard button not visible
**Solution:** Check top-right corner for orange FAB button

### Issue: Cards don't update
**Solution:** Try sending multiple commands, wait 2 seconds between

### Issue: App crashes on open
**Solution:** Send crash log from logcat:
```bash
adb logcat | grep -E "(AILive|AndroidRuntime|FATAL)"
```

---

## ✅ Next Steps After Testing

Once you've tested:

1. **If Everything Works:**
   - ✅ Report: "Phase 6.1 dashboard working perfectly!"
   - We'll move to Phase 6.2 (Data Visualization)

2. **If Issues Found:**
   - Report issues with details (see checklist)
   - I'll fix and push new build
   - Re-test after fixes

3. **Suggestions Welcome:**
   - UI/UX improvements
   - Feature requests
   - Performance feedback

---

**Current GitHub Actions Build:** https://github.com/Ishabdullah/AILive/actions

Check status there or wait for my update!
