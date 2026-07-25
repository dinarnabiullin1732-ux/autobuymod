@echo off
setlocal
cd /d "%~dp0"

set "GRADLE_VERSION=8.14"
set "GRADLE_BAT=%CD%\.tools\gradle-%GRADLE_VERSION%\bin\gradle.bat"

echo.
echo === Environment check ===
echo Project dir: %CD%
echo.

echo Java:
java -version
if errorlevel 1 (
    echo [ERROR] Java not found.
) else (
    echo [OK] Java command works.
)

echo.
if exist "%GRADLE_BAT%" (
    echo Local Gradle:
    call "%GRADLE_BAT%" --version
) else (
    echo [WARN] Local Gradle not installed yet.
    echo Run 00_SETUP_AND_BUILD.cmd first.
)

echo.
pause
