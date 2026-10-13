param(
    [string]$ProjectPath = 'G:\workspace\unity-stream-app\unity-xr-stream'
)
$ErrorActionPreference = 'Stop'
$taskRepository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskProject = [IO.Path]::GetFullPath($ProjectPath)
if (-not (Test-Path -LiteralPath (Join-Path $taskProject 'ProjectSettings\ProjectVersion.txt'))) {
    throw "Not a Unity project: $taskProject"
}
$taskSource = Join-Path $taskRepository 'unity-port\Assets\XRStream'
$taskPlugins = Join-Path $taskRepository 'unity-bridge\build\unity-runtime'
if (-not (Test-Path -LiteralPath (Join-Path $taskPlugins 'unity-bridge-debug.aar'))) {
    throw 'Run .\gradlew.bat :unity-bridge:exportUnityRuntime first.'
}
$taskDestination = Join-Path $taskProject 'Assets\XRStream'
$taskCopied = 0
foreach ($taskFile in Get-ChildItem -LiteralPath $taskSource -File -Recurse) {
    $taskRelative = [IO.Path]::GetRelativePath($taskSource, $taskFile.FullName)
    $taskTarget = Join-Path $taskDestination $taskRelative
    New-Item -ItemType Directory -Path (Split-Path -Parent $taskTarget) -Force | Out-Null
    Copy-Item -LiteralPath $taskFile.FullName -Destination $taskTarget -Force
    $taskCopied++
}
$taskAndroid = Join-Path $taskDestination 'Plugins\Android'
New-Item -ItemType Directory -Path $taskAndroid -Force | Out-Null
# Remove only the dependency artifacts exported by the earlier version of this script.
# Maven now resolves these together with Unity's AndroidX dependencies.
$taskOldArtifacts = @(
    'annotation-experimental-1.5.0.aar', 'annotation-jvm-1.9.1.jar', 'annotations-23.0.0.jar',
    'bcprov-jdk18on-1.76.jar', 'collection-jvm-1.5.0.jar', 'core-common-2.2.0.jar',
    'core-runtime-2.2.0.aar', 'jcifs-ng-2.1.10.jar', 'jspecify-1.0.0.jar',
    'kotlin-stdlib-2.4.10.jar', 'kotlinx-coroutines-android-1.11.0.jar',
    'kotlinx-coroutines-core-jvm-1.11.0.jar', 'room-common-jvm-2.8.4.jar',
    'room-ktx-2.8.4.aar', 'room-runtime.aar', 'slf4j-api-1.7.36.jar',
    'sqlite-framework.aar', 'sqlite.aar'
)
$taskOwnedRoot = [IO.Path]::GetFullPath($taskAndroid).TrimEnd('\') + '\'
foreach ($taskArtifact in $taskOldArtifacts) {
    foreach ($taskName in @($taskArtifact, "$taskArtifact.meta")) {
        $taskOldPath = [IO.Path]::GetFullPath((Join-Path $taskAndroid $taskName))
        if (-not $taskOldPath.StartsWith($taskOwnedRoot, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Artifact path escaped the generated plugin directory: $taskOldPath"
        }
        if (Test-Path -LiteralPath $taskOldPath -PathType Leaf) { Remove-Item -LiteralPath $taskOldPath }
    }
}
foreach ($taskFile in Get-ChildItem -LiteralPath $taskPlugins -File) {
    if ($taskFile.Extension -notin '.aar', '.jar') { continue }
    Copy-Item -LiteralPath $taskFile.FullName -Destination (Join-Path $taskAndroid $taskFile.Name) -Force
    $taskCopied++
}
$taskTemplateDestination = Join-Path $taskProject 'Assets\Plugins\Android'
New-Item -ItemType Directory -Path $taskTemplateDestination -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $taskRepository 'unity-port\Assets\Plugins\Android\mainTemplate.gradle') `
    -Destination (Join-Path $taskTemplateDestination 'mainTemplate.gradle') -Force
$taskCopied++
Write-Output "Copied $taskCopied source/plugin files to $taskDestination"
