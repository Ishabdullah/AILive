# Whisper Model Directory

This directory should contain the Whisper ASR model file for speech recognition.

## Required File:
- **ggml-small.en.bin** - Whisper small English model (~466MB)

## Download Instructions:

1. Download the model from Hugging Face:
   ```
   https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en.bin
   ```

2. Place the downloaded file in this directory:
   ```
   app/src/main/assets/models/whisper/ggml-small.en.bin
   ```

3. The model will be automatically extracted to internal storage on first app launch

## Model Details:
- **Format**: GGML binary format (.bin)
- **Size**: ~466MB
- **Language**: English only
- **Quality**: Good balance between size and accuracy
- **Sample Rate**: 16kHz
- **Channels**: Mono

## Alternative Models:
You can also use other Whisper models by updating the filename in `WhisperAssetExtractor.kt`:
- `ggml-tiny.en.bin` (~75MB) - Faster but less accurate
- `ggml-base.en.bin` (~142MB) - Good balance
- `ggml-small.en.bin` (~466MB) - Better accuracy (recommended)
- `ggml-medium.en.bin` (~1.5GB) - High accuracy but slower

## Important Notes:
1. The model file is NOT included in the repository due to its large size
2. You must download and add it manually before building the APK
3. The app will check for the model on startup and extract it if needed
4. Without this model, voice recognition will not work