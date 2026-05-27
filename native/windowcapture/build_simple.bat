@echo off
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat" >nul 2>&1
cd /d "D:\ZM\yizgzq\yiz1.21.1\native\windowcapture"
"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Tools\MSVC\14.44.35207\bin\Hostx64\x64\cl.exe" /EHsc /O2 /Fe:WindowCapture.dll /I"C:\Program Files\Java\jdk-21\include" /I"C:\Program Files\Java\jdk-21\include\win32" /I"include" src\dllmain.cpp src\DXGICapture.cpp src\GDICapture.cpp src\WindowEnumerator.cpp src\InputInjector.cpp src\JNIBridge.cpp /link /DLL /OUT:WindowCapture.dll dxgi.lib d3d11.lib dxguid.lib user32.lib gdi32.lib dwmapi.lib
exit /b %ERRORLEVEL%
