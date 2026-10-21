cmake_minimum_required(VERSION 3.10.2)
project("warp" LANGUAGES C)

# Set C standard
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Set output library name
set(LIB_NAME warp)

# Android configuration
if(ANDROID)
    set(CMAKE_ANDROID_NDK ${CMAKE_ANDROID_NDK})
    set(CMAKE_ANDROID_API ${CMAKE_ANDROID_API})
    set(CMAKE_SYSTEM_NAME Android)
    set(CMAKE_ANDROID_ARCH_ABI ${ANDROID_ABI})
endif()

# Include directories
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# Source files - only files that actually exist
set(SRCS
    warp.c
    crypto/chacha20poly1305.c
    crypto/poly1305.c
    crypto/curve25519.c
    crypto/blake2s.c
    amnezia/obfuscation.c
    wireguard/noise.c
    wireguard/handshake.c
    wireguard/peer.c
    wireguard/timers.c
    wireguard/device.c
    wireguard/queue.c
    platform/android/jni.c
    platform/android/net.c
)

# Platform-specific sources
if(ANDROID)
    list(APPEND SRCS platform/android/jni.c)
    list(APPEND SRCS platform/android/net.c)
endif()

# Create the shared library
add_library(${LIB_NAME} SHARED ${SRCS})

# Link libraries
target_link_libraries(${LIB_NAME}
    ${CMAKE_DL_LIBS}
    ${CMAKE_THREAD_LIBS_INIT}
    log
    android
)

# Compile options
target_compile_options(${LIB_NAME} PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter
    -Wno-unused-variable
    -fvisibility=hidden
    -D_GNU_SOURCE
    -DHAVE_CONFIG_H
)

# Platform-specific compile definitions
if(ANDROID)
    target_compile_definitions(${LIB_NAME} PRIVATE
        -DANDROID
        -D__ANDROID__
        -D__ANDROID_API__=${CMAKE_ANDROID_API}
    )
else()
    target_compile_definitions(${LIB_NAME} PRIVATE
        -DLINUX
    )
endif()

# Install headers
install(FILES warp.h DESTINATION include)

# Installation
install(TARGETS ${LIB_NAME}
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
    RUNTIME DESTINATION bin
)

# Enable testing (tests directory not present)
# enable_testing()
# add_subdirectory(tests)