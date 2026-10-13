param(
    [string]$Serial,
    [string]$AdbPath = 'D:\util\unityHub\editor\6000.6.5f1\Editor\Data\PlaybackEngines\AndroidPlayer\SDK\platform-tools\adb.exe',
    [string]$OutputDirectory,
    [string]$PackageName = 'blackark.app.vr.unity'
)
$ErrorActionPreference = 'Stop'
if ($PackageName -notmatch '^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$') { throw 'Invalid Android package name.' }
if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) { throw "ADB does not exist: $AdbPath" }
$taskDeviceLines = & $AdbPath devices -l
if ($LASTEXITCODE -ne 0) { throw 'Failed to query ADB devices.' }
$taskDevices = @($taskDeviceLines | Where-Object { $_ -match '^\S+\s+device(\s|$)' } |
    ForEach-Object { ($_ -split '\s+')[0] })
if (-not $Serial) {
    if ($taskDevices.Count -eq 0) { throw 'Connect Galaxy XR with USB debugging enabled and accept its debugging prompt.' }
    if ($taskDevices.Count -gt 1) { throw 'Multiple devices connected. Specify -Serial.' }
    $Serial = $taskDevices[0]
}
if ($Serial -notin $taskDevices) { throw "Device is not authorized or connected: $Serial" }
function Invoke-DeviceAdb([string[]]$Arguments) {
    $taskResult = & $AdbPath -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($Arguments[0])" }
    return ($taskResult -join [Environment]::NewLine).Trim()
}
if (-not $OutputDirectory) {
    $taskRepository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    $OutputDirectory = Join-Path $taskRepository ('build\unity-port\device\' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$taskPackage = Invoke-DeviceAdb @('shell', 'dumpsys', 'package', $PackageName)
$taskSnapshot = [ordered]@{
    capturedAt = (Get-Date -Format o)
    serial = $Serial
    model = Invoke-DeviceAdb @('shell', 'getprop', 'ro.product.model')
    android = Invoke-DeviceAdb @('shell', 'getprop', 'ro.build.version.release')
    api = Invoke-DeviceAdb @('shell', 'getprop', 'ro.build.version.sdk')
    package = $PackageName
    packageDetails = @($taskPackage -split '\r?\n' | Where-Object {
        $_ -match 'versionCode=|versionName=|minSdk=|targetSdk=|primaryCpuAbi=|ACCESS_LOCAL_NETWORK|org.khronos.openxr.permission'
    } | ForEach-Object Trim)
}
$taskExitInfo = Invoke-DeviceAdb @('shell', 'dumpsys', 'activity', 'exit-info', $PackageName)
$taskExitInfo | Set-Content -LiteralPath (Join-Path $OutputDirectory 'exit-info.txt') -Encoding utf8
$taskPidOutput = & $AdbPath -s $Serial shell pidof $PackageName 2>$null
$taskAppPid = ($taskPidOutput -join ' ').Trim() -split '\s+' | Select-Object -First 1
$taskProcessRunning = $taskAppPid -match '^\d+$'
if (-not $taskProcessRunning) {
    $taskPreviousPid = [regex]::Match($taskExitInfo, 'timestamp=[^\r\n]*?\bpid=(\d+)')
    if ($taskPreviousPid.Success) { $taskAppPid = $taskPreviousPid.Groups[1].Value }
}
$taskSnapshot.processRunning = $taskProcessRunning
$taskSnapshot.processId = $taskAppPid
$taskSnapshot | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'device.json') -Encoding utf8
if ($taskAppPid -match '^\d+$') {
    $taskLog = Invoke-DeviceAdb @('logcat', '-d', '-v', 'threadtime', '--pid', $taskAppPid)
    $taskLog | Set-Content -LiteralPath (Join-Path $OutputDirectory 'logcat.txt') -Encoding utf8
    Write-Output "Captured app log (PID $taskAppPid, running=$taskProcessRunning)."
} else {
    Write-Output 'Device details captured. Launch XR Stream and rerun to capture its app log.'
}
Write-Output "Diagnostics: $([IO.Path]::GetFullPath($OutputDirectory))"
