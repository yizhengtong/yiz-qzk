$ErrorActionPreference = "Stop"

Write-Host "============================================"
Write-Host " Building WindowCapture.dll"
Write-Host "============================================"

$vcvars = "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
$jniInclude = "C:\Program Files\Java\jdk-21\include"
$jniWin32 = "C:\Program Files\Java\jdk-21\include\win32"

if (!(Test-Path $vcvars)) { Write-Error "vcvars64.bat not found."; exit 1 }
if (!(Test-Path "$jniInclude\jni.h")) { Write-Error "jni.h not found."; exit 1 }

$msvcBin = "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Tools\MSVC"
$msvcVer = Get-ChildItem $msvcBin -Directory | Sort-Object Name -Descending | Select-Object -First 1
$clExe = Join-Path $msvcVer.FullName "bin\Hostx64\x64\cl.exe"
Write-Host "[build] Using cl.exe: $clExe"

$buildDir = "D:\ZM\yizgzq\yiz1.21.1\native\windowcapture"

# Write a temp build script that cmd can execute cleanly
$tempBat = Join-Path $env:TEMP "build_windowcapture.bat"
@"
@echo off
call "$vcvars" >nul
cd /d "$buildDir"
"$clExe" /EHsc /O2 /Fe:WindowCapture.dll /I"$jniInclude" /I"$jniWin32" /I"include" src\dllmain.cpp src\DXGICapture.cpp src\GDICapture.cpp src\WindowEnumerator.cpp src\InputInjector.cpp src\JNIBridge.cpp /link /DLL /OUT:WindowCapture.dll dxgi.lib d3d11.lib dxguid.lib user32.lib gdi32.lib dwmapi.lib
exit /b %ERRORLEVEL%
"@ | Out-File -FilePath $tempBat -Encoding ASCII

Write-Host "[build] Compiling via: $tempBat"

$proc = Start-Process -FilePath "cmd.exe" -ArgumentList "/c `"$tempBat`"" -Wait -NoNewWindow -PassThru
$exitCode = $proc.ExitCode

Remove-Item $tempBat -Force -ErrorAction SilentlyContinue

if ($exitCode -eq 0) {
    Write-Host "[build] SUCCESS" -ForegroundColor Green
    Get-Item "$buildDir\WindowCapture.dll" | Select-Object Name, Length, LastWriteTime
} else {
    Write-Host "[build] FAILED with exit code $exitCode" -ForegroundColor Red
    exit $exitCode
}
