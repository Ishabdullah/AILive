#!/bin/bash

MODEL_DIR="app/src/main/assets/models"
mkdir -p "$MODEL_DIR"

echo "Downloading MobileNetV2 model..."

# Download MobileNetV2 TFLite model
curl -L "https://tfhub.dev/tensorflow/lite-model/mobilenet_v2_1.0_224/1/metadata/1?lite-format=tflite" \
    -o "$MODEL_DIR/mobilenet_v2.tflite"

# Download labels
curl -L "https://raw.githubusercontent.com/tensorflow/models/master/research/slim/datasets/imagenet_2012_validation_synset_labels.txt" \
    -o "$MODEL_DIR/labels.txt"

echo "✓ Models downloaded to $MODEL_DIR"
ls -lh "$MODEL_DIR"
