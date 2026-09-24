$ErrorActionPreference = "Stop"

$adbPath = "C:\AndroidSDK\platform-tools\adb.exe"
$emulatorPath = "C:\AndroidSDK\emulator\emulator.exe"
$avdName = "AndreiCopy_VSCode_API_30"
$packageName = "ph.dragonview.mobile"

function Get-OnlineEmulatorSerial {
    foreach ($line in (& $adbPath devices)) {
        if ($line -match "^(emulator-\d+)\s+device$") {
            return $Matches[1]
        }
    }
    return $null
}

& $adbPath start-server | Out-Null

$serial = Get-OnlineEmulatorSerial
if ($serial) {
    $runningAvd = (& $adbPath -s $serial emu avd name 2>$null | Select-Object -First 1)
    if ($runningAvd -eq $avdName) {
        $installedPackage = & $adbPath -s $serial shell pm list packages $packageName 2>$null
        if ($installedPackage -match "package:$([regex]::Escape($packageName))") {
            & $adbPath -s $serial shell monkey -p $packageName -c android.intent.category.LAUNCHER 1 | Out-Null
            Write-Host "DragonView emulator is already running on $serial and the app has been opened."
        }
        else {
            Write-Host "DragonView emulator is already running on $serial, but the app is not installed yet."
        }
        exit 0
    }
    throw "Another Android emulator is already running ($runningAvd). Close it, then run this task again."
}

$emulatorArguments = @(
    "-avd", $avdName,
    "-memory", "1024",
    "-lowram",
    "-no-snapshot-load",
    "-no-snapshot-save",
    "-gpu", "swiftshader_indirect",
    "-no-audio",
    "-no-boot-anim",
    "-netdelay", "none",
    "-netspeed", "full"
)

Start-Process -FilePath $emulatorPath -ArgumentList $emulatorArguments -WindowStyle Normal

$deadline = (Get-Date).AddMinutes(3)
do {
    Start-Sleep -Seconds 3
    $serial = Get-OnlineEmulatorSerial
    if ($serial) {
        $bootComplete = (& $adbPath -s $serial shell getprop sys.boot_completed 2>$null).Trim()
        if ($bootComplete -eq "1") {
            $installedPackage = & $adbPath -s $serial shell pm list packages $packageName 2>$null
            if ($installedPackage -match "package:$([regex]::Escape($packageName))") {
                & $adbPath -s $serial shell monkey -p $packageName -c android.intent.category.LAUNCHER 1 | Out-Null
                Write-Host "DragonView emulator is ready on $serial and the app has been opened."
            }
            else {
                Write-Host "DragonView emulator is ready on $serial, but the app is not installed yet."
            }
            exit 0
        }
    }
} while ((Get-Date) -lt $deadline)

throw "The emulator did not finish booting within three minutes. Close memory-heavy applications and try again."
