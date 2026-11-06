cmake_minimum_required(VERSION 3.22 FATAL_ERROR)

project(js8core-jni VERSION 1.0.0 LANGUAGES C CXX)

# Require C++20
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ft8_lib (Karlis Goba, MIT, vendor/ft8_lib): FT8 decoder for the FT8 monitor
set(FT8LIB_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../vendor/ft8_lib)
add_library(ft8lib STATIC
  ${FT8LIB_ROOT}/ft8/decode.c
  ${FT8LIB_ROOT}/ft8/ldpc.c
  ${FT8LIB_ROOT}/ft8/osd.c
  ${FT8LIB_ROOT}/ft8/crc.c
  ${FT8LIB_ROOT}/ft8/message.c
  ${FT8LIB_ROOT}/ft8/pack.c
  ${FT8LIB_ROOT}/ft8/unpack.c
  ${FT8LIB_ROOT}/ft8/text.c
  ${FT8LIB_ROOT}/ft8/constants.c
  ${FT8LIB_ROOT}/ft8/encode.c
  ${FT8LIB_ROOT}/common/monitor.c
  ${FT8LIB_ROOT}/fft/kiss_fft.c
  ${FT8LIB_ROOT}/fft/kiss_fftr.c
)
target_include_directories(ft8lib PUBLIC ${FT8LIB_ROOT})
target_compile_options(ft8lib PRIVATE -Wno-unused-function)
set_target_properties(ft8lib PROPERTIES POSITION_INDEPENDENT_CODE ON)

# Create the JNI library
add_library(js8core-jni SHARED
  ft8_monitor.cpp
  bluetooth_serial_bridge_jni.cpp
  hamlib_rig_control_jni.cpp
  js8_engine_jni.cpp
  js8_jni_methods.cpp
  usb_serial_bridge_jni.cpp
)

target_compile_features(js8core-jni PUBLIC cxx_std_20)

# Include directories
target_include_directories(js8core-jni
  PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# Link against core and Android adapter
target_link_libraries(js8core-jni
  PRIVATE
    js8core::js8core
    js8core::android-adapter
    ft8lib
)

# Android-specific configuration
if(ANDROID)
  # JNI headers are provided by NDK automatically
  # No need to find_package(JNI) for Android builds

  # Hamlib static library for rig model enumeration
  set(HAMLIB_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../android/libs/hamlib/${ANDROID_ABI}")
  if(EXISTS "${HAMLIB_ROOT}/lib/libhamlib.a")
    set(HAMLIB_LIB "${HAMLIB_ROOT}/lib/libhamlib.a")
    target_include_directories(js8core-jni PRIVATE "${HAMLIB_ROOT}/include")
    if(CMAKE_BUILD_TYPE STREQUAL "Release")
      target_link_libraries(js8core-jni PRIVATE
        -Wl,--whole-archive
        "${HAMLIB_LIB}"
        -Wl,--no-whole-archive
        m
      )
    else()
      target_link_libraries(js8core-jni PRIVATE "${HAMLIB_LIB}" m)
    endif()
  else()
    message(FATAL_ERROR "Hamlib not found at ${HAMLIB_ROOT}. Build hamlib for ${ANDROID_ABI}.")
  endif()

  # Strip symbols in release builds
  if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_link_options(js8core-jni PRIVATE
      -Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/js8core-jni.map
      -Wl,--gc-sections
      -Wl,--strip-all
    )
  endif()
else()
  # For non-Android builds, find JNI
  find_package(JNI REQUIRED)
  target_include_directories(js8core-jni
    PRIVATE
      ${JNI_INCLUDE_DIRS}
  )
endif()

# Compiler warnings
if(CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
  target_compile_options(js8core-jni PRIVATE
    -Wall
    -Wextra
    -Wpedantic
    -Wno-unused-parameter
  )
endif()

# Export only JNI_OnLoad and JNI methods
set_target_properties(js8core-jni PROPERTIES
  CXX_VISIBILITY_PRESET hidden
  VISIBILITY_INLINES_HIDDEN ON
)

# Install library (not needed for Android builds)
if(NOT ANDROID)
  install(TARGETS js8core-jni
    LIBRARY DESTINATION lib
    RUNTIME DESTINATION lib
  )
endif()
