param([string]$FfmpegPath, [string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
if (-not $FfmpegPath) { $FfmpegPath = (Get-Command ffmpeg -ErrorAction Stop).Source }
if (-not $OutputDirectory) {
    $taskRepository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    $OutputDirectory = Join-Path $taskRepository 'build\unity-port\fixtures'
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$taskVideo = Join-Path $OutputDirectory 'XRStream_AudioSrt.mp4'
if (-not (Test-Path -LiteralPath $taskVideo)) {
    & $FfmpegPath -hide_banner -loglevel error -n `
        -f lavfi -i 'testsrc2=size=640x360:rate=30' `
        -f lavfi -i 'sine=frequency=440:sample_rate=48000' `
        -f lavfi -i 'sine=frequency=880:sample_rate=48000' `
        -map 0:v -map 1:a -map 2:a -t 24 -c:v libx264 -preset veryfast -pix_fmt yuv420p `
        -c:a aac -b:a 96k -metadata:s:a:0 'language=kor' -metadata:s:a:1 'language=eng' `
        -movflags +faststart $taskVideo
    if ($LASTEXITCODE -ne 0) { throw 'FFmpeg fixture generation failed.' }
}
$taskAssVideo = Join-Path $OutputDirectory 'XRStream_Ass.mp4'
if (-not (Test-Path -LiteralPath $taskAssVideo)) { Copy-Item -LiteralPath $taskVideo -Destination $taskAssVideo }
@'
1
00:00:01,000 --> 00:00:05,000
한글 SRT 자막 테스트
Korean SRT subtitle test

2
00:00:06,000 --> 00:00:10,000
오디오 1: 낮은 소리 440 Hz
오디오 2: 높은 소리 880 Hz

3
00:00:11,000 --> 00:00:22,000
seek 후에도 이 자막이 시간에 맞게 표시됩니다.
'@ | Set-Content -LiteralPath (Join-Path $OutputDirectory 'XRStream_AudioSrt.ko.srt') -Encoding utf8
@'
[Script Info]
ScriptType: v4.00+
PlayResX: 640
PlayResY: 360

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Noto Sans KR,26,&H0000FFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,0,2,10,10,20,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:01.00,0:00:07.00,Default,,0,0,0,,한글 ASS 자막\N노란색과 {\c&H000000FF&}빨간색{\r}
Dialogue: 0,0:00:08.00,0:00:15.00,Default,,0,0,0,,seek 후에도 자막 시간이 맞습니다.
Dialogue: 0,0:00:16.00,0:00:23.00,Default,,0,0,0,,Display에서 자막을 끄고 다시 켜 보세요.
'@ | Set-Content -LiteralPath (Join-Path $OutputDirectory 'XRStream_Ass.ko.ass') -Encoding utf8
Write-Output "Fixtures: $([IO.Path]::GetFullPath($OutputDirectory))"
