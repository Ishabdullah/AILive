cmake_minimum_required(VERSION 3.22.1)

project(ailive_llm)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Optimization flags for ARM
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -march=armv8-a+dotprod+i8mm+bf16")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -march=armv8-a+dotprod+i8mm+bf16")

# --- Check for external dependencies ---
set(EXTERNAL_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external)

# --- llama.cpp (for LLM) ---
set(LLAMA_CPP_DIR ${EXTERNAL_DIR}/llama.cpp)
set(USE_LLAMA_CPP OFF)

if(EXISTS ${LLAMA_CPP_DIR} AND EXISTS ${LLAMA_CPP_DIR}/CMakeLists.txt)
    message(STATUS "Found llama.cpp at ${LLAMA_CPP_DIR}")
    set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    add_subdirectory(${LLAMA_CPP_DIR} llama.cpp)
    set(USE_LLAMA_CPP ON)
else()
    message(WARNING "llama.cpp not found or incomplete - using fallback implementation")
    # Create stub library
    add_library(llama STATIC)
    target_sources(llama PRIVATE)
    set(USE_LLAMA_CPP OFF)
endif()

# --- whisper.cpp (for STT) ---
set(WHISPER_CPP_DIR ${EXTERNAL_DIR}/whisper.cpp)
set(USE_WHISPER_CPP OFF)

if(EXISTS ${WHISPER_CPP_DIR} AND EXISTS ${WHISPER_CPP_DIR}/CMakeLists.txt)
    message(STATUS "Found whisper.cpp at ${WHISPER_CPP_DIR}")
    set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_MAIN OFF CACHE BOOL "" FORCE)
    add_subdirectory(${WHISPER_CPP_DIR} whisper.cpp)
    set(USE_WHISPER_CPP ON)
else()
    message(WARNING "whisper.cpp not found or incomplete - using fallback implementation")
    # Create stub library
    add_library(whisper STATIC)
    target_sources(whisper PRIVATE)
    set(USE_WHISPER_CPP OFF)
endif()

# --- piper (for TTS) ---
set(PIPER_DIR ${EXTERNAL_DIR}/piper)
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
    ailive_audio.cpp         # Source file for audio JNI functions
)

# Include directories
target_include_directories(ailive_llm PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

if(USE_LLAMA_CPP)
    target_include_directories(ailive_llm PRIVATE ${LLAMA_CPP_DIR})
endif()

if(USE_WHISPER_CPP)
    target_include_directories(ailive_llm PRIVATE ${WHISPER_CPP_DIR})
endif()

if(EXISTS ${PIPER_DIR})
    target_include_directories(ailive_llm PRIVATE ${PIPER_DIR}/src/cpp)
endif()

# Link libraries
target_link_libraries(ailive_llm
    llama       # From llama.cpp or stub
    whisper     # From whisper.cpp or stub
    piper_lib   # From piper (stub library)
    log         # Android logging
)

# Add compile definitions to indicate which components are available
target_compile_definitions(ailive_llm PRIVATE
    USE_LLAMA_CPP=${USE_LLAMA_CPP}
    USE_WHISPER_CPP=${USE_WHISPER_CPP}
)

# Platform-specific configurations
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_definitions(ailive_llm PRIVATE ARM64)
endif()