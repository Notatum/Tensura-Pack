param(
    [string]$GameDir = (Join-Path $env:APPDATA '.tlauncher\legacy\Minecraft\game'),
    [string]$SourceMods = '',
    [string]$CacheDir = (Join-Path $env:LOCALAPPDATA 'TensuraUpdater'),
    [string]$PackUrl = '',
    [switch]$NoLaunch
)
$ErrorActionPreference='Stop'
Write-Host '[Tensura] Esta entrada ahora utiliza el updater administrado de GitHub.'
& (Join-Path $PSScriptRoot 'Actualizar-Tensura-Cliente.ps1') -GameDir $GameDir -CacheDir $CacheDir -PackUrl $PackUrl -NoLaunch:$NoLaunch
exit $LASTEXITCODE
