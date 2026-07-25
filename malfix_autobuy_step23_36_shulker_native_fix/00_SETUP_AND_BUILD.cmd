@echo off
setlocal
cd /d "%~dp0"

set "GRADLE_VERSION=8.14"
set "TOOLS_DIR=%CD%\.tools"
set "GRADLE_HOME=%TOOLS_DIR%\gradle-%GRADLE_VERSION%"
set "GRADLE_BAT=%GRADLE_HOME%\bin\gradle.bat"

echo.
echo === Malfix AutoBuy: setup and build ===
echo Project dir: %CD%
echo.

if not exist "%GRADLE_BAT%" (
    echo Local Gradle not found.
    echo Installing Gradle %GRADLE_VERSION% into .tools...
    powershell -NoProfile -ExecutionPolicy Bypass -File "%CD%\tools\setup_gradle.ps1" "%GRADLE_VERSION%" "%TOOLS_DIR%"
    if errorlevel 1 (
        echo.
        echo [ERROR] Gradle setup failed.
        pause
        exit /b 1
    )
)

echo.
echo Checking Java:
java -version
if errorlevel 1 (
    echo.
    echo [ERROR] Java not found. Install Java 21 and try again.
    pause
    exit /b 1
)

echo.
echo Checking Gradle:
call "%GRADLE_BAT%" --version
if errorlevel 1 (
    echo.
    echo [ERROR] Local Gradle failed.
    pause
    exit /b 1
)

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
echo Jar files are in:
echo %CD%\build\libs
echo.
pause
