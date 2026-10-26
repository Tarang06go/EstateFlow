[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$AppImagePath,
    [Parameter(Mandatory = $true)]
    [string]$OutputDirectory,
    [Parameter(Mandatory = $true)]
    [string]$InstallerPath,
    [Parameter()]
    [string]$AppVersion = '1.3.0'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$versionPattern = '^\d+(\.\d+){1,3}$'
if ($AppVersion -notmatch $versionPattern) { throw 'AppVersion must be numeric, for example 1.0.0.' }
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$appImageFull = (Resolve-Path -LiteralPath $AppImagePath).Path
$installerFull = (Resolve-Path -LiteralPath $InstallerPath).Path
if ((Get-Item -LiteralPath $installerFull).Length -le 0) { throw 'The Windows installer is empty.' }
$appImageName = "EstateFlow-$AppVersion-win-x64"
if ((Split-Path -Leaf $appImageFull) -ne $appImageName) { throw "Expected app image folder '$appImageName'." }
foreach ($required in @((Join-Path $appImageFull "$appImageName.exe"),
        (Join-Path $appImageFull 'app\estateflow.jar'), (Join-Path $appImageFull 'runtime\lib\modules'),
        (Join-Path $appImageFull 'runtime\legal'))) {
    if (!(Test-Path -LiteralPath $required)) { throw "App image is incomplete; missing $required" }
}

$outputFull = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $outputFull | Out-Null
$archivePath = Join-Path $outputFull "EstateFlow_Windows_${AppVersion}_Release.zip"
$checksumPath = Join-Path $outputFull "EstateFlow_Windows_${AppVersion}_Release.sha256"
$installerName = "EstateFlow-$AppVersion-Windows-x64-Setup.exe"
$installerOutput = Join-Path $outputFull $installerName
$installerChecksum = "$installerOutput.sha256"
$readmeOutput = Join-Path $outputFull 'README_INSTALL.txt'
$notesOutput = Join-Path $outputFull "RELEASE_NOTES_$AppVersion.md"
if (Test-Path -LiteralPath $archivePath) { throw "Preserving existing release archive: $archivePath" }
if (Test-Path -LiteralPath $checksumPath) { throw "Preserving existing checksum manifest: $checksumPath" }
foreach ($existing in @($installerChecksum, $readmeOutput, $notesOutput)) {
    if (Test-Path -LiteralPath $existing) { throw "Preserving existing release file: $existing" }
}
if ((Test-Path -LiteralPath $installerOutput) -and
        [System.IO.Path]::GetFullPath($installerOutput) -ne [System.IO.Path]::GetFullPath($installerFull)) {
    throw "Preserving an existing installer at a different path: $installerOutput"
}

$stageName = '.estateflow-release-stage-' + [guid]::NewGuid().ToString('N')
$stagePath = Join-Path $outputFull $stageName
$stageFull = [System.IO.Path]::GetFullPath($stagePath)
if (!$stageFull.StartsWith($outputFull + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Temporary release staging must stay inside the selected output directory.'
}

try {
    New-Item -ItemType Directory -Path $stageFull | Out-Null
    Copy-Item -LiteralPath $appImageFull -Destination $stageFull -Recurse
    foreach ($name in @('docs', 'db', 'db\migrations', 'scripts', 'LICENSES')) { New-Item -ItemType Directory -Path (Join-Path $stageFull $name) -Force | Out-Null }
    foreach ($name in @('RELEASE_AND_BACKUP.md', "RELEASE_NOTES_$AppVersion.md", "THIRD_PARTY_NOTICES_$AppVersion.md",
            'FACIAL_VERIFICATION_IMPLEMENTATION_STATUS.md', 'PHASE_11_RENT_COLLECTION_INSIGHTS.md', 'DATABASE_FIX_RENT_COLLECTION_INSIGHTS.md')) {
        Copy-Item -LiteralPath (Join-Path $projectRoot "docs\$name") -Destination (Join-Path $stageFull "docs\$name")
    }
    Copy-Item -LiteralPath (Join-Path $projectRoot 'db\db.properties.example') -Destination (Join-Path $stageFull 'db\db.properties.example')
    Copy-Item -LiteralPath (Join-Path $projectRoot 'db\schema.sql') -Destination (Join-Path $stageFull 'db\schema.sql')
    Get-ChildItem -LiteralPath (Join-Path $projectRoot 'db\migrations') -Filter 'v*.sql' -File | Copy-Item -Destination (Join-Path $stageFull 'db\migrations')
    Copy-Item -LiteralPath (Join-Path $projectRoot 'scripts\download-face-models.ps1') -Destination (Join-Path $stageFull 'scripts\download-face-models.ps1')
    Copy-Item -LiteralPath (Join-Path $projectRoot 'src\main\resources\fonts\Inter-OFL.txt') -Destination (Join-Path $stageFull 'LICENSES\Inter-OFL.txt')
    Copy-Item -LiteralPath (Join-Path $projectRoot "docs\THIRD_PARTY_NOTICES_$AppVersion.md") -Destination (Join-Path $stageFull "LICENSES\THIRD_PARTY_NOTICES_$AppVersion.md")

    @"
EstateFlow for Windows x64 — version $AppVersion

Run EstateFlow-$AppVersion-win-x64.exe in the EstateFlow-$AppVersion-win-x64 folder.

Requirements: supported Windows x64 and access to MySQL 8.4. The app image bundles Java; MySQL is installed and administered separately. Prepare the database from the EstateFlow schema and required manual migrations before first sign-in. EstateFlow does not apply migrations or restore backups at startup.

Create %USERPROFILE%\.estateflow\db.properties from db\db.properties.example and enter connection details for the intended schema. Keep configured credentials private and outside the application folder. No administrator account is preset; use first-administrator setup after the schema is ready.

Documents, preferences, and logs are stored under %USERPROFILE%\.estateflow. Optional facial verification is disabled by default. It has no validated liveness or presentation-attack protection; password sign-in remains available. Face models are a separate, hash-verified download.

See docs\RELEASE_AND_BACKUP.md for database setup and backup/restore procedures, docs\RELEASE_NOTES_$AppVersion.md for changes, and db\schema.sql plus db\migrations for fresh database setup. Retain LICENSES and the app image runtime\legal notices when redistributing.
"@ | Set-Content -Encoding UTF8 (Join-Path $stageFull 'README_INSTALL.txt')

    $appFolder = Join-Path $appImageFull 'app'
    foreach ($spec in @(
            @((Join-Path $appFolder 'mysql-connector-j-26.7.0.jar'), 'LICENSE', 'MySQL-Connector-J-LICENSE.txt'),
            @((Join-Path $appFolder 'slf4j-api-2.0.17.jar'), 'META-INF/LICENSE.txt', 'SLF4J-LICENSE.txt'))) {
        $jar = [System.IO.Compression.ZipFile]::OpenRead($spec[0])
        try {
            $entry = $jar.GetEntry($spec[1])
            if (!$entry) { throw "License entry '$($spec[1])' missing from $($spec[0])" }
            $source = $entry.Open()
            $destination = [System.IO.File]::Create((Join-Path $stageFull "LICENSES\$($spec[2])"))
            try { $source.CopyTo($destination) } finally { $source.Dispose(); $destination.Dispose() }
        } finally { $jar.Dispose() }
    }

    [System.IO.Compression.ZipFile]::CreateFromDirectory($stageFull, $archivePath, [System.IO.Compression.CompressionLevel]::Optimal, $false)
    & (Join-Path $PSScriptRoot 'verify-release.ps1') -ArchivePath $archivePath -ChecksumPath $checksumPath -AppVersion $AppVersion
    if ([System.IO.Path]::GetFullPath($installerFull) -ne [System.IO.Path]::GetFullPath($installerOutput)) {
        Copy-Item -LiteralPath $installerFull -Destination $installerOutput
    }
    Copy-Item -LiteralPath (Join-Path $stageFull 'README_INSTALL.txt') -Destination $readmeOutput
    Copy-Item -LiteralPath (Join-Path $projectRoot "docs\RELEASE_NOTES_$AppVersion.md") -Destination $notesOutput
    $installerHash = (Get-FileHash -LiteralPath $installerOutput -Algorithm SHA256).Hash.ToLowerInvariant()
    "$installerHash  $installerName" | Set-Content -LiteralPath $installerChecksum -Encoding ascii
    Write-Output "Windows installer: $installerOutput"
    Write-Output "Portable release ZIP: $archivePath"
    Write-Output "Installer SHA-256 manifest: $installerChecksum"
} finally {
    if (Test-Path -LiteralPath $stageFull) {
        if (!$stageFull.StartsWith($outputFull + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing to remove a release staging directory outside the selected output folder.'
        }
        Remove-Item -LiteralPath $stageFull -Recurse -Force
    }
}
