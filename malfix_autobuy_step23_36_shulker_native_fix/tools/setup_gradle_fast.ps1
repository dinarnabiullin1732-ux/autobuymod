param(
    [string]$Version = "8.14",
    [string]$ToolsDir = ""
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

if ([string]::IsNullOrWhiteSpace($ToolsDir)) {
    $ToolsDir = Join-Path $env:USERPROFILE ".malfix_tools"
}

if (!(Test-Path -LiteralPath $ToolsDir)) {
    New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null
}

$ToolsDir = (Resolve-Path -LiteralPath $ToolsDir).Path
$GradleDir = Join-Path $ToolsDir ("gradle-" + $Version)
$GradleBat = Join-Path $GradleDir "bin\gradle.bat"

if (Test-Path -LiteralPath $GradleBat) {
    Write-Host "Gradle already installed:"
    Write-Host $GradleBat
    exit 0
}

$ZipPath = Join-Path $ToolsDir ("gradle-" + $Version + "-bin.zip")
$Url = "https://services.gradle.org/distributions/gradle-$Version-bin.zip"

Write-Host "Downloading Gradle $Version once into shared cache:"
Write-Host $ToolsDir
Write-Host $Url

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

if (Test-Path -LiteralPath $ZipPath) {
    Remove-Item -LiteralPath $ZipPath -Force
}

# Silenced progress is much faster in Windows PowerShell.
Invoke-WebRequest -Uri $Url -OutFile $ZipPath -UseBasicParsing

Write-Host "Extracting..."
Expand-Archive -Path $ZipPath -DestinationPath $ToolsDir -Force

if (!(Test-Path -LiteralPath $GradleBat)) {
    throw "gradle.bat was not found after extraction: $GradleBat"
}

Write-Host "Gradle installed:"
Write-Host $GradleBat
exit 0
