[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ArchivePath,
    [Parameter()]
    [string]$ChecksumPath,
    [Parameter()]
    [string]$AppVersion = '1.3.0'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archiveFull = (Resolve-Path -LiteralPath $ArchivePath).Path
if ([string]::IsNullOrWhiteSpace($ChecksumPath)) { $ChecksumPath = "$archiveFull.sha256" }
$zip = [System.IO.Compression.ZipFile]::OpenRead($archiveFull)
try {
    $names = @($zip.Entries | ForEach-Object FullName)
    $duplicates = $names | Group-Object | Where-Object Count -gt 1
    if ($duplicates) { throw "Duplicate archive paths: $($duplicates.Name -join ', ')" }
    $unsafe = $names | Where-Object { $_ -match '(^/|^[A-Za-z]:|(^|/)\.\.(/|$))' }
    if ($unsafe) { throw "Unsafe archive paths: $($unsafe -join ', ')" }
    $sqlFiles = @($names | Where-Object { $_ -match '\.sql$' })
    $allowedSql = @('db/schema.sql') + @(1..8 | ForEach-Object { 'db/migrations/v1.' + $_ + '_' + (@('audit_log','app_user','audit_actor','expense','deposits_leases','documents_inspections','vendors_work_orders','face_enrollment')[$_ - 1]) + '.sql' })
    $unexpectedSql = @($sqlFiles | Where-Object { $allowedSql -notcontains $_ })
    if ($unexpectedSql) { throw "Release contains unexpected SQL files (including possible backups): $($unexpectedSql -join ', ')" }
    $forbidden = $names | Where-Object { $_ -match '(^|/)\.git(/|$)|(^|/)\.idea(/|$)|(^|/)target(/|$)|(^|/)logs(/|$)|(^|/)db\.properties$|(^|/)db-test\.properties$|(^|/)\.estateflow(/|$)|(^|/)documents/|\.cnf$' }
    if ($forbidden) { throw "Release contains forbidden local/build data: $($forbidden -join ', ')" }
    foreach ($required in @('README_INSTALL.txt','db/db.properties.example','db/schema.sql','scripts/download-face-models.ps1','docs/RELEASE_AND_BACKUP.md',
            "docs/RELEASE_NOTES_$AppVersion.md","docs/THIRD_PARTY_NOTICES_$AppVersion.md",
            'docs/DATABASE_FIX_RENT_COLLECTION_INSIGHTS.md','LICENSES/Inter-OFL.txt')) {
        if ($names -notcontains $required) { throw "Required release file is missing: $required" }
    }
    foreach ($required in $allowedSql) {
        if ($names -notcontains $required) { throw "Required fresh-install SQL is missing: $required" }
    }
    foreach ($entry in $zip.Entries) {
        $stream = $entry.Open()
        try { $stream.CopyTo([System.IO.Stream]::Null) } finally { $stream.Dispose() }
    }
    $config = $zip.GetEntry('db/db.properties.example')
    $reader = [System.IO.StreamReader]::new($config.Open())
    try { $example = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if ($example -notmatch '(?m)^db\.password=REPLACE_WITH_YOUR_LOCAL_PASSWORD\s*$') {
        throw 'The shipped database example does not contain the expected safe password placeholder.'
    }
} finally {
    $zip.Dispose()
}
$hash = (Get-FileHash -LiteralPath $archiveFull -Algorithm SHA256).Hash.ToLowerInvariant()
$manifestFull = [System.IO.Path]::GetFullPath($ChecksumPath)
"$hash  $([System.IO.Path]::GetFileName($archiveFull))" | Set-Content -LiteralPath $manifestFull -Encoding ascii
Write-Output "Archive entries verified: $($names.Count)"
Write-Output "No local configuration, database dumps, user data, IDE files, or build output found."
Write-Output "SHA-256 manifest: $manifestFull"
