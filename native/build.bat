@echo off
echo [build] Starting...
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat" >nul
echo [build] vcvars done, cd to native...
cd /d "D:\ZM\yizgzq\yiz1.21.1\native"
echo [build] Current dir: %CD%
echo [build] Looking for narrow_klass.c...
if exist narrow_klass.c (echo [build] narrow_klass.c FOUND) else (echo [build] narrow_klass.c NOT FOUND)
"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Tools\MSVC\14.44.35207\bin\Hostx64\x64\cl.exe" /Fe:narrow_klass.dll /I"C:\Program Files\Java\jdk-21\include" /I"C:\Program Files\Java\jdk-21\include\win32" narrow_klass.c /link /DLL /OUT:narrow_klass.dll
echo [build] Exit code: %ERRORLEVEL%
