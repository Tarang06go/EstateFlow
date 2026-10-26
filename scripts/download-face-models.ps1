[CmdletBinding()]
param([string]$OutputDirectory = (Join-Path $env:LOCALAPPDATA 'EstateFlow\models'))

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) { throw 'Windows Local AppData is required.' }
$models = @(
    @{ Name = 'face_detection_yunet_2023mar.onnx'; Url = 'https://media.githubusercontent.com/media/opencv/opencv_zoo/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx'; Sha256 = '8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4' },
    @{ Name = 'face_recognition_sface_2021dec.onnx'; Url = 'https://media.githubusercontent.com/media/opencv/opencv_zoo/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx'; Sha256 = '0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79' }
)
$directory = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $directory | Out-Null
foreach ($model in $models) {
    $destination = Join-Path $directory $model.Name
    if (Test-Path -LiteralPath $destination -PathType Leaf) {
        $existing = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($existing -eq $model.Sha256) { Write-Output "Already verified: $destination ($existing)"; continue }
        throw "An existing file has the expected model name but the wrong SHA-256. It was preserved: $destination"
    }
    $temporary = Join-Path $directory ('.' + [guid]::NewGuid().ToString('N') + '.download')
    try {
        Invoke-WebRequest -Uri $model.Url -OutFile $temporary -TimeoutSec 180
        $actual = (Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actual -ne $model.Sha256) { throw "SHA-256 verification failed for $($model.Name). The downloaded file was discarded." }
        Move-Item -LiteralPath $temporary -Destination $destination -Force
        Write-Output "Verified model: $destination ($actual)"
    } finally {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
    }
}
Write-Output 'Model license and provenance notices: https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet and https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface'
