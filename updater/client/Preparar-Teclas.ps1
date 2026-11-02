param(
    [Parameter(Mandatory=$true)][string]$GameDir,
    [Parameter(Mandatory=$true)][string]$PolicyFile,
    [switch]$Force
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath $GameDir).Path.TrimEnd('\')
$policy=Get-Content -LiteralPath $PolicyFile -Raw -Encoding UTF8 | ConvertFrom-Json
if ($policy.schema -ne 1 -or $policy.revision -notmatch '^[A-Za-z0-9_.-]+$' -or !$policy.bindings) { throw 'El perfil de teclas publicado no es válido.' }
$stateFile=Join-Path $root '.tensura-client-release.json'
$previous=if (Test-Path -LiteralPath $stateFile -PathType Leaf) { Get-Content -LiteralPath $stateFile -Raw -Encoding UTF8 | ConvertFrom-Json } else { $null }
if (!$Force -and $previous -and $previous.keybindingsRevision -eq $policy.revision) {
    Write-Host '[Tensura] Perfil de teclas ya aplicado; se conservan las personalizaciones.'
    return [pscustomobject]@{revision=$policy.revision;applied=$false;updated=0}
}
$path=Join-Path $root 'options.txt'
if (Test-Path -LiteralPath $path) {
    if ((Get-Item -LiteralPath $path -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'El archivo de opciones no puede ser un enlace.' }
    $text=[IO.File]::ReadAllText($path)
} else { $text='' }
$updated=0
$protected=@('key.attack','key.use','key.forward','key.left','key.back','key.right','key.jump','key.sneak','key.sprint','key.drop','key.inventory','key.chat','key.playerlist','key.pickItem','key.command','key.socialInteractions','key.screenshot','key.togglePerspective','key.fullscreen','key.swapOffhand','key.advancements')
foreach ($entry in $policy.bindings.PSObject.Properties) {
    $id=[string]$entry.Name;$binding=[string]$entry.Value
    if ($id -notmatch '^[A-Za-z0-9_.]+$' -or ($id -in $protected) -or $id -like 'key.hotbar.*') { throw "El perfil intenta cambiar una tecla básica: $id" }
    if ($binding -notmatch '^key\.(keyboard|mouse)\.[a-z0-9.]+(?::(?:CONTROL|SHIFT|ALT))?$') { throw "Asignación de tecla no válida: $id" }
    $line='key_'+$id+':'+$binding
    $pattern='(?m)^'+[regex]::Escape('key_'+$id)+':[^\r\n]*'
    if ([regex]::IsMatch($text,$pattern)) {
        $text=[regex]::Replace($text,$pattern,[Text.RegularExpressions.MatchEvaluator]{param($match) $line})
    } else { $text=$text.TrimEnd()+"`n"+$line+"`n" }
    $updated++
}
[IO.File]::WriteAllText($path,$text,[Text.UTF8Encoding]::new($false))
Write-Host "[Tensura] Perfil de teclas aplicado: prioridad para Tensura y complementos ($updated asignaciones)."
return [pscustomobject]@{revision=$policy.revision;applied=$true;updated=$updated}
