cmake_minimum_required(VERSION 3.22.1)
project(stocksense_llm LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# Fetch llama.cpp (includes BitNet 1-bit kernel support)
# Pin to a known-good commit so builds are reproducible.
# ---------------------------------------------------------------------------
include(FetchContent)
FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggerganov/llama.cpp.git
    GIT_TAG        b4568
    GIT_SHALLOW    TRUE
    GIT_PROGRESS   TRUE
)

# Build llama.cpp as a static lib linked into our shared JNI lib.
set(LLAMA_STATIC  ON  CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(llama_cpp)

# ---------------------------------------------------------------------------
# JNI wrapper – bridges Kotlin (LlamaCpp.kt) <-> C++ llama.cpp API
# ---------------------------------------------------------------------------
add_library(llama SHARED
    llama_jni.cpp
)

target_include_directories(llama PRIVATE
    ${llama_cpp_SOURCE_DIR}/include
    ${llama_cpp_SOURCE_DIR}/ggml/include
)

find_library(log-lib log)

target_link_libraries(llama PRIVATE
    llama
    ggml
    common
    ${log-lib}
)

# Strip debug symbols for release to keep APK small.
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_link_options(llama PRIVATE -s)
endif()
