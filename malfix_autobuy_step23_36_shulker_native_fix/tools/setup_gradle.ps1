param(
    [string]$Version = "8.14",
    [string]$ToolsDir = ".tools"
)

$ErrorActionPreference = "Stop"

if (!(Test-Path -LiteralPath $ToolsDir)) {
    New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null
}

$ToolsDir = (Resolve-Path -LiteralPath $ToolsDir).Path
$GradleDir = Join-Path $ToolsDir ("gradle-" + $Version)
$GradleBat = Join-Path $GradleDir "bin\gradle.bat"

if (Test-Path -LiteralPath $GradleBat) {
    Write-Host "Gradle already installed: $GradleDir"
    exit 0
}

$ZipPath = Join-Path $ToolsDir ("gradle-" + $Version + "-bin.zip")
$Url = "https://services.gradle.org/distributions/gradle-$Version-bin.zip"

Write-Host "Downloading Gradle $Version..."
Write-Host $Url

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

if (Test-Path -LiteralPath $ZipPath) {
    Remove-Item -LiteralPath $ZipPath -Force
}

Invoke-WebRequest -Uri $Url -OutFile $ZipPath

Write-Host "Extracting..."
Expand-Archive -Path $ZipPath -DestinationPath $ToolsDir -Force

if (!(Test-Path -LiteralPath $GradleBat)) {
    throw "gradle.bat was not found after extraction: $GradleBat"
}

Write-Host "Gradle installed: $GradleDir"
exit 0
