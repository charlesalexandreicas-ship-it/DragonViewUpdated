$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
$gradleWrapper = Join-Path $projectRoot "gradlew.bat"
$sourceApk = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
$releaseDirectory = Join-Path $projectRoot "releases"

Push-Location $projectRoot
try {
    & $gradleWrapper --no-daemon :app:assembleDebug
    if ($LASTEXITCODE -ne 0) {
        throw "The Android build failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $sourceApk)) {
    throw "The build completed but the debug APK was not found at $sourceApk."
}

New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null

$latestUpdate = 0
Get-ChildItem -LiteralPath $releaseDirectory -Filter "dragonView#*.apk" -File | ForEach-Object {
    if ($_.BaseName -match '^dragonView#(\d+)$') {
        $number = [int]$Matches[1]
        if ($number -gt $latestUpdate) {
            $latestUpdate = $number
        }
    }
}

$nextUpdate = $latestUpdate + 1
$releaseApk = Join-Path $releaseDirectory "dragonView#$nextUpdate.apk"
Copy-Item -LiteralPath $sourceApk -Destination $releaseApk

Write-Host "DragonView APK created: $releaseApk"
