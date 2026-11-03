param(
    [Parameter(Mandatory=$true)][string]$GameDir,
    [Parameter(Mandatory=$true)][string]$PolicyFile
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath $GameDir).Path.TrimEnd('\')
$policy=Get-Content -LiteralPath $PolicyFile -Raw -Encoding UTF8 | ConvertFrom-Json
$performance=$policy.performance
if (!$performance) { return [pscustomobject]@{revision='';applied=$false;updated=0} }
if ($policy.schema -ne 1 -or $performance.revision -notmatch '^[A-Za-z0-9_.-]+$' -or !$performance.optionCaps) { throw 'El perfil de rendimiento publicado no es válido.' }
$caps=@{}
foreach ($entry in $performance.optionCaps.PSObject.Properties) {
    $key=[string]$entry.Name
    if ($key -notin @('entityDistanceScaling','simulationDistance')) { throw "Opción de rendimiento no permitida: $key" }
    $cap=0.0
    if (![double]::TryParse([string]$entry.Value,[Globalization.NumberStyles]::Float,[Globalization.CultureInfo]::InvariantCulture,[ref]$cap) -or [double]::IsNaN($cap) -or [double]::IsInfinity($cap)) { throw "Límite inválido: $key" }
    if (($key -eq 'entityDistanceScaling' -and ($cap -lt 0.5 -or $cap -gt 1.0)) -or ($key -eq 'simulationDistance' -and ($cap -lt 5 -or $cap -gt 10 -or $cap -ne [math]::Floor($cap)))) { throw "Límite fuera de rango: $key" }
    $caps[$key]=$cap
}
$statePath=Join-Path $root '.tensura-client-release.json'
if (Test-Path -LiteralPath $statePath) {
    $previous=Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($previous.performanceRevision -eq $performance.revision) {
        Write-Host '[Tensura] Ajuste de rendimiento ya aplicado; se conservan los cambios posteriores.'
        return [pscustomobject]@{revision=$performance.revision;applied=$false;updated=0}
    }
}
$path=Join-Path $root 'options.txt'
if (Test-Path -LiteralPath $path) {
    if ((Get-Item -LiteralPath $path -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'El archivo de opciones no puede ser un enlace.' }
    $text=[IO.File]::ReadAllText($path)
} else { $text='' }
$updated=0
foreach ($key in $caps.Keys) {
    $pattern='(?m)^'+[regex]::Escape($key)+':([^\r\n]*)'
    $match=[regex]::Match($text,$pattern)
    $current=$caps[$key]
    if ($match.Success) {
        if (![double]::TryParse($match.Groups[1].Value,[Globalization.NumberStyles]::Float,[Globalization.CultureInfo]::InvariantCulture,[ref]$current) -or [double]::IsNaN($current) -or [double]::IsInfinity($current) -or $current -lt 0) { throw "Valor de opción inválido: $key" }
        if ($current -le $caps[$key]) { continue }
    }
    $replacement=$key+':'+$caps[$key].ToString('0.0############',[Globalization.CultureInfo]::InvariantCulture)
    if ($key -eq 'simulationDistance') { $replacement=$key+':'+[int]$caps[$key] }
    if ($match.Success) { $text=[regex]::Replace($text,$pattern,[Text.RegularExpressions.MatchEvaluator]{param($m) $replacement}) }
    else { $text=$text.TrimEnd()+"`n"+$replacement+"`n" }
    $updated++
}
[IO.File]::WriteAllText($path,$text,[Text.UTF8Encoding]::new($false))
Write-Host "[Tensura] Ajuste inicial de rendimiento aplicado ($updated opciones); Unbound permanece activo."
return [pscustomobject]@{revision=$performance.revision;applied=$true;updated=$updated}
