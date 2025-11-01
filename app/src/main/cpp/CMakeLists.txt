cmake_minimum_required(VERSION 3.22.1)

project(ailive_llm)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Optimization flags for ARM
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -march=armv8-a+dotprod+i8mm+bf16")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -march=armv8-a+dotprod+i8mm+bf16")

# Add llama.cpp source directory
set(LLAMA_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama.cpp)

# Check if llama.cpp exists
if(NOT EXISTS ${LLAMA_CPP_DIR})
    message(FATAL_ERROR "llama.cpp not found at ${LLAMA_CPP_DIR}. Please run: git submodule add https://github.com/ggerganov/llama.cpp.git llama.cpp")
endif()

# llama.cpp configuration
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

# Add llama.cpp as subdirectory
add_subdirectory(${LLAMA_CPP_DIR} llama.cpp)

# Our JNI library
add_library(ailive_llm SHARED
    ailive_llm.cpp
)

# Include directories
target_include_directories(ailive_llm PRIVATE
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/ggml/include
)

# Link libraries
target_link_libraries(ailive_llm
    llama
    log  # Android logging
)
