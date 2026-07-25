@echo off
setlocal
cd /d "%~dp0"

set "GRADLE_VERSION=8.14"
set "GRADLE_BAT=%CD%\.tools\gradle-%GRADLE_VERSION%\bin\gradle.bat"

if exist "%GRADLE_BAT%" (
    call "%GRADLE_BAT%" --no-daemon clean
) else (
    echo Local Gradle is missing. Nothing to clean through Gradle.
)

pause
