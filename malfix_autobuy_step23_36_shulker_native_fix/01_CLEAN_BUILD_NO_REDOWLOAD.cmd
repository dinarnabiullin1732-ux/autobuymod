@echo off
setlocal
cd /d "%~dp0"

set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"

set "GRADLE_VERSION=8.14"
set "SHARED_TOOLS=%USERPROFILE%\.malfix_tools"
set "GRADLE_BAT=%SHARED_TOOLS%\gradle-%GRADLE_VERSION%\bin\gradle.bat"

if not exist "%GRADLE_BAT%" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%CD%\tools\setup_gradle_fast.ps1" "%GRADLE_VERSION%" "%SHARED_TOOLS%"
)

if exist "%CD%\build\libs\*.jar" del /q "%CD%\build\libs\*.jar"
call "%GRADLE_BAT%" --no-daemon clean build
if errorlevel 1 (
    echo.
    echo [ERROR] Clean build failed.
    pause
    exit /b 1
)

echo.
echo [OK] Clean build finished.
echo JAR files:
dir /b "%CD%\build\libs\*.jar"
echo.
echo Copy ONLY malfix-autobuy-*.jar. Do NOT copy *sources*.jar.
pause
