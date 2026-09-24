$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
$jdkRoot = "C:\Users\Charlesalexandre\.gradle\jdks\temurin17-dragonview\jdk-17.0.20.1+1"
$androidSdk = "C:\AndroidSDK"
$releaseDirectory = Join-Path $projectRoot "releases"
$sourceApk = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path -LiteralPath $jdkRoot)) {
    throw "Required Java 17 runtime is missing: $jdkRoot"
}
if (-not (Test-Path -LiteralPath $androidSdk)) {
    throw "Android SDK is missing: $androidSdk"
}

$env:JAVA_HOME = $jdkRoot
$env:ANDROID_HOME = $androidSdk
$env:ANDROID_SDK_ROOT = $androidSdk

Push-Location $projectRoot
try {
    & .\gradlew.bat testDebugUnitTest assembleDebug --stacktrace
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $sourceApk)) {
    throw "Gradle completed without producing $sourceApk"
}

New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null
$latestUpdate = 0
Get-ChildItem -LiteralPath $releaseDirectory -Filter "dragonView#*.apk" | ForEach-Object {
    if ($_.BaseName -match '^dragonView#(\d+)$') {
        $number = [int]$Matches[1]
        if ($number -gt $latestUpdate) { $latestUpdate = $number }
    }
}

$nextUpdate = $latestUpdate + 1
$releaseApk = Join-Path $releaseDirectory "dragonView#$nextUpdate.apk"
Copy-Item -LiteralPath $sourceApk -Destination $releaseApk
Write-Host "DragonView APK created: $releaseApk"
