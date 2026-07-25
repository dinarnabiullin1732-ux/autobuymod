@echo off
setlocal
cd /d "%~dp0"

set "JAVA_HOME=%USERPROFILE%\.malfix_tools\jdk-21"
if not exist "%JAVA_HOME%\bin\java.exe" set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"

set "GRADLE_VERSION=8.14"
set "SHARED_TOOLS=%USERPROFILE%\.malfix_tools"
set "GRADLE_HOME=%SHARED_TOOLS%\gradle-%GRADLE_VERSION%"
set "GRADLE_BAT=%GRADLE_HOME%\bin\gradle.bat"

echo.
echo === Malfix AutoBuy Fast Build ===
echo Project: %CD%
echo Java: %JAVA_HOME%
echo Shared Gradle: %GRADLE_BAT%
echo.

java -version
if errorlevel 1 (
    echo.
    echo [ERROR] Java 21 not found at: %JAVA_HOME%
    echo Edit this cmd file and set JAVA_HOME to your real JDK 21 folder.
    pause
    exit /b 1
)

if not exist "%GRADLE_BAT%" (
    echo.
    echo Local shared Gradle not found. Installing it once...
    powershell -NoProfile -ExecutionPolicy Bypass -File "%CD%\tools\setup_gradle_fast.ps1" "%GRADLE_VERSION%" "%SHARED_TOOLS%"
    if errorlevel 1 (
        echo.
        echo [ERROR] Gradle setup failed.
        pause
        exit /b 1
    )
)

echo.
echo Gradle check:
call "%GRADLE_BAT%" --version
if errorlevel 1 (
    echo.
    echo [ERROR] Gradle failed.
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
echo Put ONLY this normal mod JAR into mods. Do NOT use sources.jar.
echo If you see any *sources*.jar, do not copy it.
pause
