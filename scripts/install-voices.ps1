<#
.SYNOPSIS
    Installs Piper (offline neural text-to-speech) and a few natural English voices for Sayvoy.

.DESCRIPTION
    Downloads into %USERPROFILE%\.sayvoy (or $env:SAYVOY_HOME if set):
      piper\piper.exe     the speech engine (MIT licence, about 22 MB)
      voices\*.onnx       voice models (about 60 MB each) plus their .onnx.json configs
    Safe to run again: anything already downloaded is skipped.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\install-voices.ps1
#>
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # makes Invoke-WebRequest much faster

$sayvoyHome = if ($env:SAYVOY_HOME) { $env:SAYVOY_HOME } else { Join-Path $env:USERPROFILE '.sayvoy' }

# Before the rename to Sayvoy, files lived in .sayso. Move them so nothing is downloaded twice.
$oldHome = Join-Path $env:USERPROFILE '.sayso'
if (-not $env:SAYVOY_HOME -and -not (Test-Path $sayvoyHome) -and (Test-Path $oldHome)) {
    Move-Item -Path $oldHome -Destination $sayvoyHome
    Write-Host "Moved your existing voices and settings from $oldHome to $sayvoyHome"
}
$voicesDir = Join-Path $sayvoyHome 'voices'
New-Item -ItemType Directory -Force -Path $voicesDir | Out-Null

# 1. Piper engine
$piperExe = Join-Path $sayvoyHome 'piper\piper.exe'
if (Test-Path $piperExe) {
    Write-Host "Piper already installed: $piperExe"
} else {
    $zip = Join-Path $env:TEMP 'piper_windows_amd64.zip'
    Write-Host 'Downloading Piper...'
    Invoke-WebRequest -Uri 'https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_windows_amd64.zip' -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $sayvoyHome -Force   # the zip contains a "piper" folder
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
Write-Host 'Restart Sayvoy, then press Ctrl+Alt+V to switch between voices.'
