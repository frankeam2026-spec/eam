param([string]$RuntimeDirectory = (Join-Path $PSScriptRoot '../../.local-runtime'))
$ErrorActionPreference='Stop'
$runtime=[IO.Path]::GetFullPath($RuntimeDirectory)
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$version='1.20.0'
$directory=Join-Path $runtime "qdrant-$version"
try {
  $existing=Invoke-RestMethod 'http://127.0.0.1:6333/' -TimeoutSec 2
  if($existing.version -ne $version){throw "Port 6333 is occupied by a different service/version"}
  Write-Output "Qdrant $version is already running on 127.0.0.1:6333"
  exit 0
} catch [System.Net.Http.HttpRequestException] {} catch [System.Threading.Tasks.TaskCanceledException] {}
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$archive=Join-Path $directory 'qdrant.zip'
if(-not(Test-Path -LiteralPath $archive)){
  Invoke-WebRequest "https://github.com/qdrant/qdrant/releases/download/v$version/qdrant-x86_64-pc-windows-msvc.zip" -OutFile $archive -TimeoutSec 120
}
$expected='6d13553584fd0411f00637519e293331ff0aa1fb79bd9e30db45839bb75de2be'
if((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLower() -ne $expected){throw 'Qdrant archive SHA256 mismatch'}
if(-not(Test-Path -LiteralPath (Join-Path $directory 'qdrant.exe'))){Expand-Archive -LiteralPath $archive -DestinationPath $directory}
$env:QDRANT__SERVICE__HOST='127.0.0.1'
$env:QDRANT__SERVICE__HTTP_PORT='6333'
$env:QDRANT__SERVICE__GRPC_PORT='6334'
$env:QDRANT__TELEMETRY_DISABLED='true'
$process=Start-Process -FilePath (Join-Path $directory 'qdrant.exe') -WorkingDirectory $directory -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $directory 'stdout.log') -RedirectStandardError (Join-Path $directory 'stderr.log')
$process.Id | Set-Content (Join-Path $directory 'qdrant.pid')
Write-Output "Started Qdrant PID $($process.Id), listening on 127.0.0.1:6333. Data: $directory/storage"
