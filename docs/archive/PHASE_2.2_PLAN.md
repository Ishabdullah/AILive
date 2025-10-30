# Phase 2.2: Camera Integration

## Objective
Enable real-time object recognition through device camera

## Components to Build

### 1. CameraManager
- Uses CameraX API
- Captures frames at 30 FPS
- Converts to Bitmap for ModelManager
- Handles permissions

### 2. Update MainActivity UI
- Add CameraPreview surface
- Add classification result overlay
- Show confidence percentage
- Display inference time

### 3. Pipeline Flow
Camera → Frame → ModelManager → Classification → UI Update
30fps    Bitmap   TensorFlow      Result      TextView

## Implementation Steps

**Step 1:** Add CameraX dependencies
**Step 2:** Create CameraManager.kt
**Step 3:** Update activity_main.xml layout
**Step 4:** Update MainActivity.kt to connect everything
**Step 5:** Add camera permission to AndroidManifest.xml

## Expected Result
- Open app → Camera view appears
- Point at objects → "Dog (87%)" appears on screen
- Real-time updates as camera moves
- ~30 FPS performance with GPU acceleration
