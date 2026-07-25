@echo off
setlocal
cd /d "%~dp0"

set "GRADLE_VERSION=8.14"
set "GRADLE_BAT=%CD%\.tools\gradle-%GRADLE_VERSION%\bin\gradle.bat"

if not exist "%GRADLE_BAT%" (
    echo [ERROR] Local Gradle is missing.
    echo Run 00_SETUP_AND_BUILD.cmd first.
    pause
    exit /b 1
)

call "%GRADLE_BAT%" --no-daemon runClient
if errorlevel 1 (
    echo.
    echo [ERROR] runClient failed. Copy the full console text and send it.
    pause
    exit /b 1
)

pause
