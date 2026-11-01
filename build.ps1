# 本地一键构建。所有工具链都在 .toolchain 里，不碰系统的 JDK/SDK。
# 用法：powershell -ExecutionPolicy Bypass -File .\build.ps1

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

if (-not (Test-Path "$root\.toolchain\jdk")) {
    Write-Error "找不到 .toolchain，先把 JDK / Android SDK / Gradle 放进去"
    exit 1
}

$tc = "$root\.toolchain"
$jdk = (Get-ChildItem "$tc\jdk" -Directory | Select-Object -First 1).FullName
$gradle = (Get-ChildItem "$tc\gradle" -Directory | Select-Object -First 1).FullName

$env:JAVA_HOME = $jdk
$env:GRADLE_USER_HOME = "$tc\.gradle"
$env:ANDROID_HOME = "$tc\android-sdk"
$env:ANDROID_USER_HOME = "$tc\.android"
$env:TEMP = "$tc\tmp"
$env:TMP = "$tc\tmp"
$env:USE_CN_MIRROR = "true"
$env:PATH = "$jdk\bin;$env:PATH"

Set-Location $root
& "$gradle\bin\gradle.bat" assembleDebug assembleRelease --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Copy-Item "$root\app\build\outputs\apk\release\app-release.apk" "$root\StarWOL-1.0.0.apk" -Force
Copy-Item "$root\app\build\outputs\apk\debug\app-debug.apk" "$root\StarWOL-1.0.0-debug.apk" -Force
Write-Host "构建完成 -> StarWOL-1.0.0.apk"
