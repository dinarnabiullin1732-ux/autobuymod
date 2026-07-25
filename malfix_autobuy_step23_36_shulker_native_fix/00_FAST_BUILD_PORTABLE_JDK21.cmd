@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

set "JAVA_HOME=%USERPROFILE%\.malfix_tools\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"

set "GRADLE_VERSION=8.14"
set "SHARED_TOOLS=%USERPROFILE%\.malfix_tools"
set "GRADLE_BAT=%SHARED_TOOLS%\gradle-%GRADLE_VERSION%\bin\gradle.bat"

echo.
echo === Malfix AutoBuy Portable JDK Build ===
echo Project: %CD%
echo Java: %JAVA_HOME%
echo Gradle: %GRADLE_BAT%
echo.

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] Portable JDK 21 not found: %JAVA_HOME%
    echo Run install_jdk21_zip_malfix.cmd first or edit JAVA_HOME in this file.
    pause
    exit /b 1
)

java -version
if errorlevel 1 (
    echo [ERROR] Java failed.
    pause
    exit /b 1
)

if not exist "%GRADLE_BAT%" (
    echo [ERROR] Shared Gradle not found: %GRADLE_BAT%
    echo Use 00_FAST_BUILD_NO_REDOWLOAD.cmd once to install Gradle, or copy your gradle-8.14 folder to .malfix_tools.
    pause
    exit /b 1
)

echo.
echo Cleaning old JARs...
if exist "%CD%\build\libs\*.jar" del /q "%CD%\build\libs\*.jar"

echo.
echo Building mod...
call "%GRADLE_BAT%" --no-daemon clean build
if errorlevel 1 (
    echo.
    echo [ERROR] Build failed. Copy the full console text and send it.
    pause
    exit /b 1
)

echo.
echo [OK] Build finished.
echo JAR files:
dir /b "%CD%\build\libs\*.jar"
echo.
echo Put ONLY the normal mod JAR into mods. Do NOT use sources.jar.
pause
