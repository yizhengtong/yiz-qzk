cmake_minimum_required(VERSION 3.20)
project(WindowCapture VERSION 1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# JNI headers — override from command line or let user set in build.bat
if(NOT DEFINED JAVA_INCLUDE_DIR)
    message(FATAL_ERROR "JAVA_INCLUDE_DIR not set. Pass -DJAVA_INCLUDE_DIR=<path>")
endif()
if(NOT DEFINED JAVA_INCLUDE_WIN32_DIR)
    message(FATAL_ERROR "JAVA_INCLUDE_WIN32_DIR not set. Pass -DJAVA_INCLUDE_WIN32_DIR=<path>")
endif()

add_library(WindowCapture SHARED
    src/dllmain.cpp
    src/DXGICapture.cpp
    src/GDICapture.cpp
    src/WindowEnumerator.cpp
    src/InputInjector.cpp
    src/JNIBridge.cpp
)

target_include_directories(WindowCapture PRIVATE
    ${JAVA_INCLUDE_DIR}
    ${JAVA_INCLUDE_WIN32_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_link_libraries(WindowCapture PRIVATE
    dxgi
    d3d11
    dxguid
    user32
    gdi32
)

# Export all symbols for JNI
set_target_properties(WindowCapture PROPERTIES
    WINDOWS_EXPORT_ALL_SYMBOLS TRUE
)
