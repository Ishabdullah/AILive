#!/data/data/com.termux/files/usr/bin/bash

echo "╔═══════════════════════════════════════╗"
echo "║   AILive Build & Run (Termux Mode)   ║"
echo "╚═══════════════════════════════════════╝"
echo ""

# Check if kotlinc is installed
if ! command -v kotlinc &> /dev/null; then
    echo "✗ Kotlin compiler not found!"
    echo "Install with: pkg install kotlin"
    exit 1
fi

echo "→ Compiling AILive..."
cd ~/AILive/app/src/main/java

# Compile all Kotlin files
kotlinc -include-runtime -d ~/AILive/ailive.jar \
    com/ailive/core/**/*.kt \
    com/ailive/motor/**/*.kt \
    com/ailive/meta/**/*.kt \
    com/ailive/memory/**/*.kt \
    com/ailive/testing/*.kt \
    com/ailive/*.kt \
    2>&1 | tee ~/AILive/compile.log

if [ ${PIPESTATUS[0]} -eq 0 ]; then
    echo "✓ Compilation successful"
    echo ""
    echo "→ Running AILive tests..."
    echo ""
    
    # Run the termux runner
    java -jar ~/AILive/ailive.jar com.ailive.testing.TermuxRunner
    
    echo ""
    echo "✓ Execution complete"
else
    echo "✗ Compilation failed. Check compile.log for details:"
    tail -20 ~/AILive/compile.log
    exit 1
fi
