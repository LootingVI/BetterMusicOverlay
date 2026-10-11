$ErrorActionPreference = 'Stop'
[Console]::InputEncoding = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Reflection.Assembly]::LoadFrom((Join-Path $PSScriptRoot 'ThumbnailReader.dll'))
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType=WindowsRuntime]
$null = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType=WindowsRuntime]
$null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType=WindowsRuntime]
$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' } | Select-Object -First 1
function Await-WinRT($operation, [Type]$resultType) {
    $task = $asTask.MakeGenericMethod($resultType).Invoke($null, @($operation))
    if (-not $task.Wait(4000)) { throw 'Windows media request timed out' }
    return $task.Result
}
$manager = Await-WinRT ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$lastKey = ''
$cover = ''
$lockedPlayer = ''
while ($null -ne ($line = [Console]::ReadLine())) {
    try {
        $request = $line | ConvertFrom-Json
        $session = $manager.GetCurrentSession()
        $sessions = @($manager.GetSessions())
        $sessionList = @($sessions | ForEach-Object { @{id=$_.SourceAppUserModelId; playing=($_.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing')} })
        if ($request.preferred) {
            $session = $sessions | Where-Object { $_.SourceAppUserModelId -eq [string]$request.preferred } | Select-Object -First 1
            if ($null -eq $session) { $session = $sessions | Where-Object { $_.SourceAppUserModelId.IndexOf([string]$request.preferred, [StringComparison]::OrdinalIgnoreCase) -ge 0 } | Select-Object -First 1 }
        } else {
            $locked = $sessions | Where-Object { $_.SourceAppUserModelId -eq $lockedPlayer } | Select-Object -First 1
            $active = $sessions | Where-Object { $_.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing' } | Select-Object -First 1
            if ($null -ne $locked -and $locked.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing') { $session = $locked }
            elseif ($null -ne $active) { $session = $active }
            elseif ($null -ne $locked) { $session = $locked }
        }
        if ($null -eq $session) { [Console]::WriteLine((@{title='';sessions=$sessionList} | ConvertTo-Json -Depth 4 -Compress)); continue }
        if ($request.action -ne 'poll') {
            $ok = switch ($request.action) {
                'play' { Await-WinRT ($session.TryTogglePlayPauseAsync()) ([bool]) }
                'next' { Await-WinRT ($session.TrySkipNextAsync()) ([bool]) }
                'previous' { Await-WinRT ($session.TrySkipPreviousAsync()) ([bool]) }
                'seek' {
                    $timeline = $session.GetTimelineProperties()
                    $seconds = [Math]::Max(0, [double]$request.seconds)
                    $length = ($timeline.EndTime - $timeline.StartTime).TotalSeconds
                    if ($length -gt 0) { $seconds = [Math]::Min($length, $seconds) }
                    if ([double]::IsNaN($seconds) -or [double]::IsInfinity($seconds)) { $false }
                    else { Await-WinRT ($session.TryChangePlaybackPositionAsync([long]($timeline.StartTime.Ticks + $seconds * 10000000))) ([bool]) }
                }
                default { $false }
            }
            if (-not $ok) { [Console]::WriteLine('{"error":"This app does not support this control","control":true}') }
            continue
        }
        $lockedPlayer = $session.SourceAppUserModelId
        $media = Await-WinRT ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
        $timeline = $session.GetTimelineProperties()
        $key = $session.SourceAppUserModelId + '|' + $media.Title + '|' + $media.Artist + '|' + $media.AlbumTitle
        if ($key -ne $lastKey -or [string]::IsNullOrEmpty($cover)) {
            $cover = ''
            try {
                if ($null -ne $media.Thumbnail) {
                    $stream = Await-WinRT ($media.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
                    $cover = [ThumbnailReader]::Read($stream)
                }
            } catch { $cover = ''; [Console]::Error.WriteLine('Artwork: ' + $_.Exception.Message) }
            $lastKey = $key
        }
        $playing = $session.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing'
        $position = ($timeline.Position - $timeline.StartTime).TotalSeconds
        $duration = ($timeline.EndTime - $timeline.StartTime).TotalSeconds
        if ($playing -and $timeline.LastUpdatedTime.Year -gt 2000) { $position += [Math]::Max(0, ([DateTimeOffset]::UtcNow - $timeline.LastUpdatedTime).TotalSeconds) }
        $position = [Math]::Max(0,$position)
        if ($duration -gt 0) { $position = [Math]::Min($duration,$position) }
        $result = @{title=$media.Title; artist=$media.Artist; album=$media.AlbumTitle; source=$session.SourceAppUserModelId; playing=$playing; position=$position; duration=[Math]::Max(0,$duration); cover=$cover}
        $result.sessions = $sessionList
        [Console]::WriteLine(($result | ConvertTo-Json -Depth 4 -Compress))
    } catch {
        [Console]::Error.WriteLine($_.Exception.Message)
        [Console]::WriteLine('{"error":"Windows media access failed; retrying ..."}')
    }
}
