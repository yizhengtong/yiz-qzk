& "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
$env:PATH = [System.Environment]::GetEnvironmentVariable("PATH", "Process")
Get-ChildItem env: | Where-Object { $_.Name -match "INCLUDE|LIB|LIBPATH|PATH" } | ForEach-Object { "$($_.Name)=$($_.Value)" }
& "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Tools\MSVC\14.44.35207\bin\Hostx64\x64\cl.exe" /Fe:narrow_klass.dll /I"C:\Program Files\Java\jdk-21\include" /I"C:\Program Files\Java\jdk-21\include\win32" narrow_klass.c /link /DLL /OUT:narrow_klass.dll
