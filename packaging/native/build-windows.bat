@echo off
rem Builds the native Windows executable (GraalVM native-image) into build\native.
rem Needs GRAALVM_HOME (Oracle GraalVM JDK 17+) and the Visual Studio Build Tools (C++).
rem Usage: gradlew packageUberJarForCurrentOS  &&  packaging\native\build-windows.bat
setlocal
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat" >nul
set PATH=%GRAALVM_HOME%\bin;%PATH%
cd /d "%~dp0\..\.."
if not exist build\native mkdir build\native
call native-image --no-fallback -H:ConfigurationFileDirectories=packaging\native\config -jar build\compose\jars\WideShare-windows-x64-1.0.0.jar -o build\native\wideshare
if errorlevel 1 exit /b 1
rem Skiko loads <java.home>\bin\jawt.dll, and the app points java.home at the executable's folder.
if not exist build\native\bin mkdir build\native\bin
copy /y build\native\*.dll build\native\bin\ >nul
rem native-image builds a console program; switch to the GUI subsystem so no cmd window opens.
editbin /SUBSYSTEM:WINDOWS build\native\wideshare.exe
