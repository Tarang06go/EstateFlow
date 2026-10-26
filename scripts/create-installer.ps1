[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$AppImagePath,
    [Parameter(Mandatory = $true)]
    [string]$OutputDirectory,
    [Parameter()]
    [string]$AppVersion = '1.3.0',
    [Parameter()]
    [string]$WixBinPath = 'C:\Program Files (x86)\WiX Toolset v3.14\bin'
)

$ErrorActionPreference = 'Stop'
$versionPattern = '^\d+(\.\d+){1,3}$'
if ($AppVersion -notmatch $versionPattern) { throw 'AppVersion must be numeric, for example 1.0.0.' }
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$appImageFull = (Resolve-Path -LiteralPath $AppImagePath).Path
$outputFull = [System.IO.Path]::GetFullPath($OutputDirectory)
$appImageName = "EstateFlow-$AppVersion-win-x64"
if ((Split-Path -Leaf $appImageFull) -ne $appImageName) { throw "Expected app image folder '$appImageName'." }
foreach ($required in @((Join-Path $appImageFull "$appImageName.exe"),
        (Join-Path $appImageFull 'app\estateflow.jar'), (Join-Path $appImageFull 'runtime\lib\modules'))) {
    if (!(Test-Path -LiteralPath $required -PathType Leaf)) { throw "App image is incomplete; missing $required" }
}
$candle = Join-Path $WixBinPath 'candle.exe'
$light = Join-Path $WixBinPath 'light.exe'
if (!(Test-Path -LiteralPath $candle -PathType Leaf) -or !(Test-Path -LiteralPath $light -PathType Leaf)) {
    throw "WiX Toolset v3 candle.exe and light.exe are required. Supply their bin folder with -WixBinPath."
}
$jpackage = (Get-Command jpackage -ErrorAction Stop).Source
New-Item -ItemType Directory -Force -Path $outputFull | Out-Null
$artifactName = "EstateFlow-$AppVersion-Windows-x64-Setup.exe"
$artifactPath = Join-Path $outputFull $artifactName
if (Test-Path -LiteralPath $artifactPath) { throw "Preserving existing installer: $artifactPath" }
$buildDir = Join-Path $outputFull ('.installer-build-' + [guid]::NewGuid().ToString('N'))
$buildFull = [System.IO.Path]::GetFullPath($buildDir)
if (!$buildFull.StartsWith($outputFull + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Installer staging must stay inside the selected output directory.'
}
$iconPath = Join-Path (Join-Path $projectRoot 'target') ('EstateFlow-' + [guid]::NewGuid().ToString('N') + '.ico')
New-Item -ItemType Directory -Path $buildFull | Out-Null
try {
    Add-Type -AssemblyName System.Drawing
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class EstateFlowIconNative {
    [DllImport("gdi32.dll")] public static extern bool DeleteObject(IntPtr handle);
}
'@
    $bitmap = [System.Drawing.Bitmap]::FromFile((Join-Path $projectRoot 'src\main\resources\images\app-icon-256.png'))
    try {
        $handle = $bitmap.GetHicon()
        try {
            $iconStream = [System.IO.File]::Create($iconPath)
            try { [System.Drawing.Icon]::FromHandle($handle).Save($iconStream) }
            finally { $iconStream.Dispose() }
        }
        finally { [void][EstateFlowIconNative]::DeleteObject($handle) }
    } finally { $bitmap.Dispose() }

    $previousPath = $env:PATH
    try {
        $env:PATH = "$WixBinPath;$previousPath"
        & $jpackage '--type' 'exe' '--app-image' $appImageFull '--dest' $buildFull '--name' 'EstateFlow' '--app-version' $AppVersion `
            '--vendor' 'EstateFlow' '--description' 'EstateFlow property and rental management' '--icon' $iconPath `
            '--win-per-user-install' '--win-dir-chooser' '--win-menu' '--win-menu-group' 'EstateFlow' '--win-shortcut' `
            '--win-upgrade-uuid' '4c7e0e2a-2bed-4cae-a035-c451cad379af'
        if ($LASTEXITCODE -ne 0) { throw "jpackage installer generation failed with exit code $LASTEXITCODE." }
    } finally { $env:PATH = $previousPath }

    $generated = Join-Path $buildFull "EstateFlow-$AppVersion.exe"
    if (!(Test-Path -LiteralPath $generated -PathType Leaf) -or (Get-Item -LiteralPath $generated).Length -le 0) {
        throw 'jpackage did not produce a non-empty Windows installer.'
    }
    Move-Item -LiteralPath $generated -Destination $artifactPath
    Write-Output "Windows installer created: $artifactPath"
    Write-Output "Installer bytes: $((Get-Item -LiteralPath $artifactPath).Length)"
} finally {
    if (Test-Path -LiteralPath $iconPath) {
        $targetFull = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'target'))
        $iconFull = [System.IO.Path]::GetFullPath($iconPath)
        if (!$iconFull.StartsWith($targetFull + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing to remove a generated icon outside target/.'
        }
        Remove-Item -LiteralPath $iconFull -Force
    }
    if (Test-Path -LiteralPath $buildFull) {
        if (!$buildFull.StartsWith($outputFull + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing to remove an installer staging directory outside the selected output folder.'
        }
        Remove-Item -LiteralPath $buildFull -Recurse -Force
    }
}
