cmake_minimum_required(VERSION 3.22.1)

project("androiddiffusion")

# Enable position independent code
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Add cpufeatures library
add_library(cpufeatures STATIC
    ${ANDROID_NDK}/sources/android/cpufeatures/cpu-features.c)

# Set compiler flags
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -ftree-vectorize")
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8-a+simd")
    endif()
endif()

# Find required packages
find_library(log-lib log)

# Add memory manager library
add_library(
    memory_manager
    SHARED
    memory_manager.cpp
)

# Set properties for memory manager
set_target_properties(
    memory_manager
    PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}"
)

# Link libraries for memory manager
target_link_libraries(
    memory_manager
    PRIVATE
    ${log-lib}
    cpufeatures
)

# Add native optimizations library
add_library(
    native_optimizations
    SHARED
    native_optimizations.cpp
)

# Set properties for native optimizations
set_target_properties(
    native_optimizations
    PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}"
)

# Include directories
target_include_directories(
    native_optimizations
    PRIVATE
    ${ANDROID_NDK}/sources/android/cpufeatures
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

# Link libraries for native optimizations
target_link_libraries(
    native_optimizations
    PRIVATE
    cpufeatures
    ${log-lib}
)

# Add IntelliSense test library
add_library(
    intellisense_test
    SHARED
    intellisense_test.cpp
)

# Set properties for IntelliSense test
set_target_properties(
    intellisense_test
    PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}"
)

# Include directories for IntelliSense test
target_include_directories(
    intellisense_test
    PRIVATE
    ${ANDROID_NDK}/sources/android/cpufeatures
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

# Link libraries for IntelliSense test
target_link_libraries(
    intellisense_test
    PRIVATE
    cpufeatures
    ${log-lib}
)

# Enable NEON for ARM
if(ANDROID_ABI MATCHES "^armeabi-v7a|arm64-v8a$")
    target_compile_options(
        native_optimizations
        PRIVATE
        -mfpu=neon
    )
    target_compile_options(
        memory_manager
        PRIVATE
        -mfpu=neon
    )
    target_compile_options(
        intellisense_test
        PRIVATE
        -mfpu=neon
    )
endif()

# Enable specific optimizations for ARM64
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_definitions(
        native_optimizations
        PRIVATE
        USE_APPLE_SILICON_OPTIMIZATIONS=1
    )
    target_compile_definitions(
        memory_manager
        PRIVATE
        USE_APPLE_SILICON_OPTIMIZATIONS=1
    )
    target_compile_definitions(
        intellisense_test
        PRIVATE
        USE_APPLE_SILICON_OPTIMIZATIONS=1
    )
endif()

# Add debug logging in CMake
message(STATUS "CMAKE_LIBRARY_OUTPUT_DIRECTORY: ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")
message(STATUS "ANDROID_ABI: ${ANDROID_ABI}")
message(STATUS "BUILD_TYPE: ${CMAKE_BUILD_TYPE}")
message(STATUS "C++ FLAGS: ${CMAKE_CXX_FLAGS}") 