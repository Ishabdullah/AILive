#!/bin/bash
# AILive Model Download Script

set -e

echo "╔════════════════════════════════════════╗"
echo "║   AILive Model Download Script        ║"
echo "╚════════════════════════════════════════╝"
echo ""

MODELS_DIR="models"
mkdir -p "$MODELS_DIR"/{whisper,smollm2,mobilenetv3,bge-small,distilbert}

# Function to download with progress
download_model() {
    local name=$1
    local url=$2
    local output=$3
    
    echo "Downloading $name..."
    if command -v wget &> /dev/null; then
        wget -O "$output" "$url" --progress=bar:force 2>&1 || return 1
    else
        curl -L -o "$output" "$url" --progress-bar || return 1
    fi
    
    if [ -f "$output" ] && [ -s "$output" ]; then
        echo "✓ $name downloaded ($(du -h "$output" | cut -f1))"
        return 0
    else
        echo "✗ Failed to download $name"
        return 1
    fi
}

# Download all models
echo "This will download ~1.2 GB of models. Continue? (y/n)"
read -r response
if [[ "$response" != "y" ]]; then
    echo "Download cancelled."
    exit 0
fi

echo ""
echo "[1/5] Downloading Whisper-Tiny (145 MB)..."
download_model "Whisper-Tiny" \
    "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin" \
    "$MODELS_DIR/whisper/whisper-tiny.bin" || echo "⚠ Whisper download failed, will retry later"

echo ""
echo "[2/5] Downloading SmolLM2-360M (271 MB)..."
download_model "SmolLM2-360M" \
    "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf" \
    "$MODELS_DIR/smollm2/smollm2-360m-q4_k_m.gguf" || echo "⚠ SmolLM2 download failed, will retry later"

echo ""
echo "[3/5] Downloading MobileNetV3-Small (10 MB)..."
download_model "MobileNetV3-Small" \
    "https://storage.googleapis.com/download.tensorflow.org/models/tflite/gpu/mobile_net_v3_small_100_224_float.tflite" \
    "$MODELS_DIR/mobilenetv3/mobilenet_v3_small.tflite" || echo "⚠ MobileNetV3 download failed, will retry later"

echo ""
echo "[4/5] Downloading BGE-small-en-v1.5 (133 MB)..."
download_model "BGE-small-en-v1.5" \
    "https://huggingface.co/BAAI/bge-small-en-v1.5/resolve/main/onnx/model.onnx" \
    "$MODELS_DIR/bge-small/bge-small-en-v1.5.onnx" || echo "⚠ BGE-small download failed, will retry later"

echo ""
echo "[5/5] Downloading DistilBERT-sentiment (127 MB)..."
download_model "DistilBERT-sentiment" \
    "https://huggingface.co/distilbert/distilbert-base-uncased-finetuned-sst-2-english/resolve/main/pytorch_model.bin" \
    "$MODELS_DIR/distilbert/distilbert-sentiment.bin" || echo "⚠ DistilBERT download failed, will retry later"

echo ""
echo "╔════════════════════════════════════════╗"
echo "║   Download Complete!                  ║"
echo "╚════════════════════════════════════════╝"
echo ""

# Show what was successfully downloaded
echo "Downloaded models:"
for model_dir in "$MODELS_DIR"/*; do
    if [ -d "$model_dir" ]; then
        model_name=$(basename "$model_dir")
        model_files=$(find "$model_dir" -type f -name "*.bin" -o -name "*.gguf" -o -name "*.tflite" -o -name "*.onnx" 2>/dev/null)
        if [ -n "$model_files" ]; then
            echo "  ✓ $model_name: $(du -sh "$model_dir" | cut -f1)"
        else
            echo "  ✗ $model_name: No model files found"
        fi
    fi
done

echo ""
echo "Total size: $(du -sh $MODELS_DIR 2>/dev/null | cut -f1)"
echo "Models location: $MODELS_DIR/"
echo ""
echo "Note: Some models may need manual download from Hugging Face"
echo "Visit: https://huggingface.co for alternative downloads"
