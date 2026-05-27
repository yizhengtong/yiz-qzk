@echo off
echo ============================================
echo  Building WindowCapture.dll
echo ============================================

call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat" >nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] vcvars64.bat failed. Check VS Build Tools installation.
    exit /b 1
)

cd /d "D:\ZM\yizgzq\yiz1.21.1\native\windowcapture"

set JNI_INCLUDE=C:\Program Files\Java\jdk-21\include
set JNI_WIN32=C:\Program Files\Java\jdk-21\include\win32

if not exist "%JNI_INCLUDE%\jni.h" (
    echo [ERROR] jni.h not found at %JNI_INCLUDE%. Check JDK installation.
    exit /b 1
)

echo [build] Compiling WindowCapture.dll...

"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Tools\MSVC\14.44.35207\bin\Hostx64\x64\cl.exe" ^
    /EHsc ^
    /O2 ^
    /utf-8 ^
    /Fe:WindowCapture.dll ^
    /I"%JNI_INCLUDE%" ^
    /I"%JNI_WIN32%" ^
    /I"include" ^
    src\dllmain.cpp ^
    src\DXGICapture.cpp ^
    src\GDICapture.cpp ^
    src\WindowEnumerator.cpp ^
    src\InputInjector.cpp ^
    src\JNIBridge.cpp ^
    /link /DLL /OUT:WindowCapture.dll ^
    dxgi.lib d3d11.lib dxguid.lib user32.lib gdi32.lib dwmapi.lib

if %ERRORLEVEL% equ 0 (
    echo [build] SUCCESS: WindowCapture.dll
) else (
    echo [build] FAILED with exit code %ERRORLEVEL%
    exit /b %ERRORLEVEL%
)
