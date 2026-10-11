$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$framework = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319'
$metadata = Join-Path $env:WINDIR 'System32\WinMetadata'
$source = Join-Path $root 'native\ThumbnailReader.cs'
$output = Join-Path $root 'src\main\resources\native\ThumbnailReader.dll'
& (Join-Path $framework 'csc.exe') /nologo /target:library "/out:$output" "/r:$metadata\Windows.Storage.winmd" "/r:$metadata\Windows.Foundation.winmd" "/r:$framework\System.Runtime.dll" $source
if ($LASTEXITCODE -ne 0) { throw 'Thumbnail reader compilation failed' }
