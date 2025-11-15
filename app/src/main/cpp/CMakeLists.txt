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

# Fix: Remove pthread from piper target on Android (it's built-in)
if(ANDROID AND TARGET piper)
    get_target_property(PIPER_LINK_LIBS piper LINK_LIBRARIES)
    if(PIPER_LINK_LIBS)
        list(REMOVE_ITEM PIPER_LINK_LIBS pthread)
        set_target_properties(piper PROPERTIES LINK_LIBRARIES "${PIPER_LINK_LIBS}")
    endif()
endif()

# Create our own static library from piper source
# This allows us to link piper functionality without modifying the upstream submodule
add_library(piper_lib STATIC ${PIPER_DIR}/src/cpp/piper.cpp)

# Make piper_lib depend on the same external project dependencies as piper
# These are created by ExternalProject_Add in piper's CMakeLists.txt
if(TARGET fmt_external)
    add_dependencies(piper_lib fmt_external)
endif()
if(TARGET spdlog_external)
    add_dependencies(piper_lib spdlog_external)
endif()
if(TARGET piper_phonemize_external)
    add_dependencies(piper_lib piper_phonemize_external)
endif()

# Get the include directories from piper's target (after add_subdirectory)
get_target_property(PIPER_INCLUDE_DIRS piper INCLUDE_DIRECTORIES)
get_target_property(PIPER_LINK_DIRS piper LINK_DIRECTORIES)

# Copy the include and link directories from piper executable
target_include_directories(piper_lib PUBLIC
    ${PIPER_DIR}/src/cpp
    ${PIPER_INCLUDE_DIRS}
)

target_link_directories(piper_lib PUBLIC
    ${PIPER_LINK_DIRS}
)

# Link against the same libraries that piper executable uses
target_link_libraries(piper_lib PUBLIC
    fmt
    spdlog
    espeak-ng
    piper_phonemize
    onnxruntime
)


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
