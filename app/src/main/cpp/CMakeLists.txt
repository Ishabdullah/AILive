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
set(PIPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/piper)
if(NOT EXISTS ${PIPER_DIR})
    message(FATAL_ERROR "piper not found at ${PIPER_DIR}")
endif()

set(PIPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(PIPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)

# Build piper which creates the piper executable with all dependencies
add_subdirectory(${PIPER_DIR} piper)

# Create our own static library from piper source
# This allows us to link piper functionality without modifying the upstream submodule
add_library(piper_lib STATIC ${PIPER_DIR}/src/cpp/piper.cpp)

# Copy the include directories and link libraries from the piper executable
# We get these after add_subdirectory has configured everything
target_include_directories(piper_lib PUBLIC
    ${PIPER_DIR}/src/cpp
)

# Link against the same dependencies that piper executable uses
# These are set up by piper's CMakeLists.txt
target_link_libraries(piper_lib PUBLIC
    fmt
    spdlog
    espeak-ng
    piper_phonemize
    onnxruntime
)

# Set the same link and include directories that piper uses
get_target_property(PIPER_LINK_DIRS piper LINK_DIRECTORIES)
get_target_property(PIPER_INCLUDE_DIRS piper INCLUDE_DIRECTORIES)
if(PIPER_LINK_DIRS)
    target_link_directories(piper_lib PUBLIC ${PIPER_LINK_DIRS})
endif()
if(PIPER_INCLUDE_DIRS)
    target_include_directories(piper_lib PUBLIC ${PIPER_INCLUDE_DIRS})
endif()


# --- Our JNI Library ---
add_library(ailive_llm SHARED
    ailive_llm.cpp
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
