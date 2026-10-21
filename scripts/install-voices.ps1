<#
.SYNOPSIS
    Installs Piper (offline neural text-to-speech) and a few natural English voices for Sayso.

.DESCRIPTION
    Downloads into %USERPROFILE%\.sayso (or $env:SAYSO_HOME if set):
      piper\piper.exe     the speech engine (MIT licence, about 22 MB)
      voices\*.onnx       voice models (about 60 MB each) plus their .onnx.json configs
    Safe to run again: anything already downloaded is skipped.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\install-voices.ps1
#>
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # makes Invoke-WebRequest much faster

$saysoHome = if ($env:SAYSO_HOME) { $env:SAYSO_HOME } else { Join-Path $env:USERPROFILE '.sayso' }
$voicesDir = Join-Path $saysoHome 'voices'
New-Item -ItemType Directory -Force -Path $voicesDir | Out-Null

# 1. Piper engine
$piperExe = Join-Path $saysoHome 'piper\piper.exe'
if (Test-Path $piperExe) {
    Write-Host "Piper already installed: $piperExe"
} else {
    $zip = Join-Path $env:TEMP 'piper_windows_amd64.zip'
    Write-Host 'Downloading Piper...'
    Invoke-WebRequest -Uri 'https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_windows_amd64.zip' -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $saysoHome -Force   # the zip contains a "piper" folder
    Remove-Item $zip
    Write-Host "Installed Piper: $piperExe"
}

# 2. Voices: language/locale/speaker/quality, as laid out in the rhasspy/piper-voices repository
$voices = @(
    'en/en_US/lessac/medium',
    'en/en_US/amy/medium',
    'en/en_GB/alba/medium'
)
$base = 'https://huggingface.co/rhasspy/piper-voices/resolve/main'

foreach ($voice in $voices) {
    $parts = $voice.Split('/')
    $name = "$($parts[1])-$($parts[2])-$($parts[3])"
    foreach ($suffix in '.onnx', '.onnx.json') {
        $target = Join-Path $voicesDir "$name$suffix"
        if (Test-Path $target) { continue }
        Write-Host "Downloading $name$suffix..."
        Invoke-WebRequest -Uri "$base/$voice/$name$suffix" -OutFile $target
    }
}

Write-Host ''
Write-Host "Done. Voices in ${voicesDir}:"
Get-ChildItem $voicesDir -Filter *.onnx | ForEach-Object { Write-Host "  $($_.BaseName)" }
Write-Host 'Restart Sayso, then press Ctrl+Alt+V to switch between voices.'
