param([Parameter(Mandatory=$true)][string]$GameDir)
$ErrorActionPreference = 'Stop'
$gameRoot = (Resolve-Path -LiteralPath $GameDir).Path
$packName = 'Tensura-Espanol-2026.11.02.zip'
$language = 'es_ar'
$policyFile=Join-Path $gameRoot 'config\tensura-managed-client.json'
if (Test-Path -LiteralPath $policyFile) {
    $policy=Get-Content -LiteralPath $policyFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($policy.resourcePack -notmatch '^[^\\/]+\.zip$' -or $policy.language -notmatch '^[a-z]{2}_[a-z]{2}$') { throw 'La política de idioma no es válida.' }
    $packName=$policy.resourcePack;$language=$policy.language
}
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
if ($text -match '(?m)^lang:') { $text = [regex]::Replace($text, '(?m)^lang:[^\r\n]*', ('lang:'+$language)) }
else { $text = $text.TrimEnd() + "`nlang:$language`n" }
[IO.File]::WriteAllText($path, $text, [Text.UTF8Encoding]::new($false))
Write-Host 'Idioma espanol y paquete de traducciones activados. Las demas opciones se conservaron.'

# Upgrade clients that still run the previous updater. This downloaded hook was
# already part of the old protocol, so those PCs receive the managed engine too.
$managed=Get-Variable -Name TensuraManagedEngine -ErrorAction SilentlyContinue
$caller=@(Get-PSCallStack | Where-Object { $_.ScriptName -and (Split-Path -Leaf $_.ScriptName) -eq 'Actualizar-Tensura-Cliente.ps1' } | Select-Object -First 1)
$urlVariable=Get-Variable -Name PackUrl -ErrorAction SilentlyContinue
$cacheVariable=Get-Variable -Name CacheDir -ErrorAction SilentlyContinue
if ((!$managed -or !$managed.Value) -and $caller.Count -eq 1 -and $urlVariable -and $cacheVariable) {
    $sourceUrl=[uri]$urlVariable.Value
    $engineRoot=Join-Path $cacheVariable.Value 'migration-engine'
    New-Item -ItemType Directory -Path $engineRoot -Force | Out-Null
    $manifest=Invoke-RestMethod -UseBasicParsing -Uri ([uri]::new($sourceUrl,'updater/client/release.json'))
    if ($manifest.schema -ne 1) { throw 'La migración del actualizador no es válida.' }
    foreach ($file in $manifest.files) {
        if ($file.name -notmatch '^[A-Za-z0-9_-]+\.(ps1|json)$' -or $file.path -notmatch '^updater/client/[A-Za-z0-9_-]+\.(ps1|json)$' -or $file.sha256 -notmatch '^[a-f0-9]{64}$') { throw 'Componente de migración inválido.' }
        $target=Join-Path $engineRoot $file.name
        Invoke-WebRequest -UseBasicParsing -Uri ([uri]::new($sourceUrl,$file.path)) -OutFile $target
        if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $file.sha256) { throw 'Un componente de migración está alterado.' }
    }
    [IO.File]::WriteAllText((Join-Path $engineRoot 'release.json'),($manifest | ConvertTo-Json -Depth 30),[Text.UTF8Encoding]::new($false))
    $runtime=& (Join-Path $engineRoot 'Preparar-Runtime.ps1') -GameDir $GameDir -CacheDir $cacheVariable.Value -Java (Get-Command java.exe).Source -Template (Join-Path $engineRoot 'perfil-tensura.json') -ReleaseManifest (Join-Path $engineRoot 'release.json')
    $oldUpdater=$caller[0].ScriptName
    Copy-Item -LiteralPath $oldUpdater -Destination ($oldUpdater+'.previous') -Force
    Copy-Item -LiteralPath (Join-Path $engineRoot 'Bootstrap.ps1') -Destination $oldUpdater -Force
    $oldNoLaunch=Get-Variable -Name NoLaunch -ErrorAction SilentlyContinue
    $launchRequested=(!$oldNoLaunch -or !$oldNoLaunch.Value) -and $env:TENSURA_NO_LAUNCH -ne '1'
    # The old script still has an LL.exe launch after this hook. Suppress that
    # branch in the calling scope and route it through the new bootstrap.
    Set-Variable -Name NoLaunch -Scope 1 -Value $true
    if($launchRequested){
        & (Join-Path $engineRoot 'Bootstrap.ps1') -GameDir $GameDir -CacheDir $cacheVariable.Value -PackUrl $sourceUrl.AbsoluteUri
        if($LASTEXITCODE -ne 0){throw 'No se pudo iniciar el launcher independiente después de migrar.'}
    }
    Write-Host '[Tensura] Actualizador anterior migrado al launcher independiente.'
}
