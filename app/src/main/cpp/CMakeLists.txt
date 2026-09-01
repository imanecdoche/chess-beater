cmake_minimum_required(VERSION 3.22.1)

project("chessbeater_engine" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Optimized Compiler Flags for Mobile Engine (NEON SIMD & LTO)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_compile_options(
        -O3
        -flto
        -fomit-frame-pointer
        -fexceptions
        -frtti
        -Wall
        -Wextra
        -pthread
    )
    add_definitions(-DUSE_NEON=2 -DNO_PREFETCH)
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    add_compile_options(
        -O3
        -flto
        -mfpu=neon
        -mfloat-abi=softfp
        -fomit-frame-pointer
        -fexceptions
        -frtti
        -Wall
        -Wextra
        -pthread
    )
    add_definitions(-DUSE_NEON -DNO_PREFETCH)
else()
    add_compile_options(
        -O3
        -flto
        -fomit-frame-pointer
        -fexceptions
        -frtti
        -Wall
        -Wextra
        -pthread
    )
endif()

find_library(
    log-lib
    log
)

# 1. JNI Shared Library
add_library(
    chessbeater_engine
    SHARED
    stockfish-bridge.cpp
)

target_link_libraries(
    chessbeater_engine
    PRIVATE
    ${log-lib}
    atomic
)

# 2. Standalone Process Executable (Standard DroidFish process pattern)
add_executable(
    stockfish_bin
    stockfish-standalone.cpp
)

target_link_libraries(
    stockfish_bin
    PRIVATE
    ${log-lib}
    atomic
)
