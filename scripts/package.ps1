$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    & .\gradlew.bat build
    if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
    $dist = Join-Path $root 'dist\1.4.2'
    New-Item -ItemType Directory -Force $dist | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    foreach ($target in @('mc12111','mc2610','mc2611','mc261','mc262','mc263')) {
        $jar = Get-ChildItem (Join-Path $root "$target\build\libs") -Filter '*-1.4.2.jar' | Where-Object { $_.Name -notmatch '-sources|-dev' } | Select-Object -First 1
        if ($null -eq $jar) { throw "Missing release JAR for $target" }
        $archive = [IO.Compression.ZipFile]::OpenRead($jar.FullName)
        try {
            if ($archive.GetEntry('dev/bettermusic/DevelopmentSmoke.class')) { throw "Refusing to package smoke harness: $target" }
            if (-not $archive.GetEntry('native/ThumbnailReader.dll')) { throw "Native thumbnail reader missing: $target" }
            if (-not ($archive.Entries | Where-Object { $_.FullName -match '^META-INF/jars/snakeyaml-2.7.jar$' })) { throw "Lyricsfile parser missing: $target" }
            if (-not $archive.GetEntry('META-INF/licenses/SnakeYAML-LICENSE.txt')) { throw "Lyricsfile parser license missing: $target" }
        } finally { $archive.Dispose() }
        Copy-Item -LiteralPath $jar.FullName -Destination $dist -Force
    }
    Copy-Item -LiteralPath (Join-Path $root 'README.md') -Destination $dist -Force
    Copy-Item -LiteralPath (Join-Path $root 'LICENSE') -Destination $dist -Force
    Copy-Item -LiteralPath (Join-Path $root 'docs') -Destination $dist -Recurse -Force
    $hashes = Get-ChildItem $dist -Filter '*.jar' | Get-FileHash -Algorithm SHA256 | ForEach-Object { $_.Hash.ToLowerInvariant() + '  ' + [IO.Path]::GetFileName($_.Path) }
    $hashes | Set-Content (Join-Path $dist 'SHA256SUMS.txt')
} finally { Pop-Location }
