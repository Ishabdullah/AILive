#!/bin/bash
adb logcat -c
adb shell am force-stop com.ailive
sleep 1
adb logcat -v threadtime > /tmp/ailive_live.log &
LOGPID=$!
sleep 1
adb shell am start -n com.ailive/.MainActivity
echo "Waiting 15 seconds..."
sleep 15
kill $LOGPID
cat /tmp/ailive_live.log | grep -E "14[0-9]{3}" | grep -E "MainActivity|AILive|TensorFlow|Error|Fatal" | head -100
