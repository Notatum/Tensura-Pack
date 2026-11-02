param([Parameter(Mandatory=$true)][string]$GameDir)
$ErrorActionPreference = 'Stop'
$gameRoot = (Resolve-Path -LiteralPath $GameDir).Path
$packName = 'Tensura-Espanol-2026.11.02.zip'
if (!(Test-Path -LiteralPath (Join-Path $gameRoot ('resourcepacks\' + $packName)) -PathType Leaf)) {
    throw 'Falta el paquete de idioma. La actualizacion no cambio las opciones de Minecraft.'
}
$path = Join-Path $gameRoot 'options.txt'
$text = if (Test-Path -LiteralPath $path) { [IO.File]::ReadAllText($path) } else { '' }
$match = [regex]::Match($text, '(?m)^resourcePacks:([^\r\n]*)')
$selected = if ($match.Success) { @(ConvertFrom-Json -InputObject $match.Groups[1].Value) } else { @('vanilla') }
$selected = @($selected | Where-Object { $_ -notlike 'file/Tensura-Espanol-*.zip' }) + @('file/' + $packName)
$line = 'resourcePacks:' + (ConvertTo-Json -InputObject @($selected) -Compress)
if ($match.Success) { $text = [regex]::Replace($text, '(?m)^resourcePacks:[^\r\n]*', [Text.RegularExpressions.MatchEvaluator]{ param($m) $line }) }
else { $text = $text.TrimEnd() + "`n" + $line + "`n" }
if ($text -match '(?m)^lang:') { $text = [regex]::Replace($text, '(?m)^lang:[^\r\n]*', 'lang:es_ar') }
else { $text = $text.TrimEnd() + "`nlang:es_ar`n" }
[IO.File]::WriteAllText($path, $text, [Text.UTF8Encoding]::new($false))
Write-Host 'Idioma espanol y paquete de traducciones activados. Las demas opciones se conservaron.'
