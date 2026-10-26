[CmdletBinding()]
param(
    [Parameter()]
    [string]$OutputRoot = (Join-Path (Split-Path -Parent $PSScriptRoot) 'release'),

    [Parameter()]
    [string]$AppVersion = '1.3.0',

    [Parameter()]
    [string[]]$MavenArguments = @()
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$targetRoot = (Join-Path $projectRoot 'target')
$versionPattern = '^\d+(\.\d+){1,3}$'
if ($AppVersion -notmatch $versionPattern) {
    throw 'AppVersion must be numeric, for example 1.0.0.'
}

$java = Get-Command java -ErrorAction Stop
$jpackage = Get-Command jpackage -ErrorAction Stop
$maven = Get-Command mvn -ErrorAction Stop
$javaVersion = (& $java.Source --version 2>&1 | Select-Object -First 1)
if ($javaVersion -notmatch '25\.') {
    throw "A Java 25 JDK is required for packaging. Found: $javaVersion"
}

$outputFull = [System.IO.Path]::GetFullPath($OutputRoot)
$appImageName = "EstateFlow-$AppVersion-win-x64"
$appImagePath = Join-Path $outputFull $appImageName
if (Test-Path -LiteralPath $appImagePath) {
    throw "Output already exists; move it aside before packaging again: $appImagePath"
}
New-Item -ItemType Directory -Force -Path $outputFull | Out-Null
New-Item -ItemType Directory -Force -Path $targetRoot | Out-Null

$stageName = 'package-input-' + [guid]::NewGuid().ToString('N')
$stagePath = Join-Path $targetRoot $stageName
$stageFull = [System.IO.Path]::GetFullPath($stagePath)
$targetFull = [System.IO.Path]::GetFullPath($targetRoot)
if (-not $stageFull.StartsWith($targetFull + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Temporary package input must stay inside the project target directory.'
}

try {
    # jpackage's non-modular launcher includes every JAR in the input directory on its class path.
    & $maven.Source @MavenArguments '-DskipTests' 'package' 'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies' '-DincludeScope=runtime' "-DoutputDirectory=$stagePath"
    if ($LASTEXITCODE -ne 0) { throw "Maven packaging failed with exit code $LASTEXITCODE." }

    $builtJar = Join-Path $targetRoot "estateflow-$AppVersion.jar"
    if (-not (Test-Path -LiteralPath $builtJar -PathType Leaf)) { throw "Maven did not produce the application jar: $builtJar" }
    Copy-Item -LiteralPath $builtJar -Destination (Join-Path $stagePath 'estateflow.jar')

    & $jpackage.Source '--type' 'app-image' '--dest' $outputFull '--name' $appImageName '--app-version' $AppVersion `
        '--input' $stagePath '--main-jar' 'estateflow.jar' '--main-class' 'com.estateflow.Launcher' `
        '--java-options' '--enable-native-access=ALL-UNNAMED'
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed with exit code $LASTEXITCODE." }

    $launcher = Join-Path $appImagePath "$appImageName.exe"
    if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) { throw "jpackage did not create the expected launcher: $launcher" }
    Write-Output "Windows app image created: $appImagePath"
    Write-Output "Launcher: $launcher"
} finally {
    if (Test-Path -LiteralPath $stageFull) {
        if (-not $stageFull.StartsWith($targetFull + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing to remove a temporary package directory outside target/.'
        }
        Remove-Item -LiteralPath $stageFull -Recurse -Force
    }
}
