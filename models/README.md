# AILive Model Assets

This directory contains all AI models used by AILive. Models are downloaded separately to keep the Git repository lightweight.

## Directory Structure
models/
├── whisper/               # Speech Recognition (145 MB)
│   └── whisper-tiny-int8.tflite
├── smollm2/              # Language Model (271 MB)
│   └── smollm2-360m-q4_k_m.gguf
├── mobilenetv3/          # Object Detection (10 MB)
│   ├── mobilenet_v3_small.tflite
│   └── labels.txt
├── bge-small/            # Text Embeddings (133 MB)
│   ├── bge-small-en-v1.5.onnx
│   ├── config.json
│   └── tokenizer.json
└── distilbert/           # Sentiment Analysis (127 MB)
├── distilbert-sentiment.tflite
└── config.json

## Model Details

### 1. Whisper-Tiny (int8)
- **Purpose:** Automatic speech recognition
- **Agent:** Language AI
- **Format:** TensorFlow Lite
- **Size:** 145 MB (int8 quantized)
- **License:** MIT
- **Source:** https://github.com/openai/whisper

**Integration:**
val interpreter = Interpreter(File("models/whisper/whisper-tiny-int8.tflite"))

---

### 2. SmolLM2-360M (Q4_K_M)
- **Purpose:** Language understanding, reasoning
- **Agent:** Meta AI
- **Format:** GGUF (llama.cpp compatible)
- **Size:** 271 MB (Q4_K_M quantized)
- **License:** Apache 2.0
- **Source:** https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct

**Integration:**
// Use llama.cpp Android bindings
val llm = LlamaModel("models/smollm2/smollm2-360m-q4_k_m.gguf")

---

### 3. MobileNetV3-Small
- **Purpose:** Object detection, image classification
- **Agent:** Visual AI
- **Format:** TensorFlow Lite
- **Size:** 10 MB
- **License:** Apache 2.0
- **Source:** https://github.com/tensorflow/models

**Integration:**
val interpreter = Interpreter(File("models/mobilenetv3/mobilenet_v3_small.tflite"))
val labels = File("models/mobilenetv3/labels.txt").readLines()

---

### 4. BGE-small-en-v1.5
- **Purpose:** Text embeddings for semantic search
- **Agent:** Memory AI
- **Format:** ONNX
- **Size:** 133 MB
- **Dimensions:** 384
- **License:** MIT
- **Source:** https://huggingface.co/BAAI/bge-small-en-v1.5

**Integration:**
val session = OrtEnvironment.getEnvironment()
.createSession("models/bge-small/bge-small-en-v1.5.onnx")

---

### 5. DistilBERT-sentiment
- **Purpose:** Sentiment analysis
- **Agent:** Emotion AI
- **Format:** TensorFlow Lite
- **Size:** 127 MB
- **License:** Apache 2.0
- **Source:** https://huggingface.co/distilbert-base-uncased-finetuned-sst-2-english

**Integration:**
val interpreter = Interpreter(File("models/distilbert/distilbert-sentiment.tflite"))

---

## Total Model Sizes

- **Storage Required:** ~1.19 GB
- **Runtime RAM:** ~3.9 GB (all loaded)
- **Lazy Loading:** Load only needed models to reduce RAM

## Downloading Models

### Option 1: Automated Script (Recommended)
cd ~/AILive
./download_models.sh

### Option 2: Manual Download

Follow the curl commands in each model's directory README.

### Option 3: From GitHub Release

Download pre-packaged models from:
https://github.com/Ishabdullah/AILive/releases

## Model Loading Strategy

**Cold Start (all models):**
- Time: ~5-8 seconds
- RAM: ~3.9 GB

**Lazy Loading (on-demand):**
- Time: <500ms per model
- RAM: Only what's needed

**Recommended:**
- Load Meta AI (SmolLM2) immediately
- Load others on first use
- Unload unused models after 5 min idle

## File Formats

- **TFLite (.tflite):** TensorFlow Lite for Android
- **ONNX (.onnx):** Open Neural Network Exchange
- **GGUF (.gguf):** GPT-Generated Unified Format (llama.cpp)

## Adding to APK

Models can be:
1. **Bundled in APK** (increases APK size to ~1.2 GB)
2. **Downloaded on first run** (smaller APK, requires internet once)
3. **Loaded from external storage** (user provides models)

**Recommended:** Option 2 (download on first run with progress bar)

## Gitignore

Models are excluded from Git via `.gitignore`:
models//*.tflite
models//.onnx
models/**/.gguf
models//*.bin
models//*.pt

Only READMEs and configs are tracked.

---

**Last Updated:** October 27, 2025  
**AILive Version:** 0.1 (Foundation)
