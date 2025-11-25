cmake_minimum_required(VERSION 3.22.1)

project(ailive_llm)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Optimization flags for ARM
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -march=armv8-a+dotprod+i8mm+bf16")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -march=armv8-a+dotprod+i8mm+bf16")

# --- llama.cpp (for LLM) ---
set(LLAMA_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp)
if(NOT EXISTS ${LLAMA_CPP_DIR})
    message(FATAL_ERROR "llama.cpp not found at ${LLAMA_CPP_DIR}")
endif()

set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
add_subdirectory(${LLAMA_CPP_DIR} llama.cpp)

# --- whisper.cpp (for STT) ---
set(WHISPER_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/whisper.cpp)
if(NOT EXISTS ${WHISPER_CPP_DIR})
    message(FATAL_ERROR "whisper.cpp not found at ${WHISPER_CPP_DIR}")
endif()

set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_MAIN OFF CACHE BOOL "" FORCE)
add_subdirectory(${WHISPER_CPP_DIR} whisper.cpp)

# --- piper (for TTS) ---
# TEMPORARY: Piper disabled due to ExternalProject incompatibility with Android NDK
# The piper submodule uses ExternalProject_Add which builds deps for x86 Linux instead of ARM64 Android
# TODO: Either use pre-built piper libs or implement native Android TTS as fallback
set(PIPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/piper)
if(NOT EXISTS ${PIPER_DIR})
    message(WARNING "piper not found at ${PIPER_DIR} - TTS will be disabled")
else()
    message(STATUS "Piper found but skipped (ExternalProject incompatible with Android NDK)")
    message(STATUS "TTS functionality will use Android system TTS as fallback")
endif()

# Create a stub piper_lib to satisfy link requirements
# The actual TTS will fall back to Android's native TextToSpeech API
add_library(piper_lib INTERFACE)
message(STATUS "Piper library: Using stub (TTS disabled, will use Android TTS fallback)")


# --- Our JNI Library ---
add_library(ailive_llm SHARED
    ailive_llm.cpp
    ailive_llm_fallback.cpp  # Fallback implementation when llama.cpp fails
    ailive_audio.cpp  # Source file for audio JNI functions
)

# Include directories
target_include_directories(ailive_llm PRIVATE
    ${LLAMA_CPP_DIR}
    ${WHISPER_CPP_DIR}
    ${PIPER_DIR}/src/cpp
)

# Link libraries
target_link_libraries(ailive_llm
    llama       # From llama.cpp
    whisper     # From whisper.cpp
    piper_lib   # From piper (static library)
    log         # Android logging
)
