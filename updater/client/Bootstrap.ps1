param(
    [Parameter(Position=0)][ValidateSet('', 'repair')][string]$Action='',
    [ValidateSet('Incremental','Strict')][string]$Mode='Incremental',
    [string]$GameDir=(Join-Path $env:APPDATA '.tlauncher\legacy\Minecraft\game'),
    [string]$CacheDir=(Join-Path $env:LOCALAPPDATA 'TensuraUpdater'),
    [string]$PackUrl='',
    [switch]$NoLaunch,
    [string]$LauncherDataRoot=(Join-Path $env:LOCALAPPDATA 'TensuraLauncher'),
    [switch]$PrepareLauncherOnly
)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
$OutputEncoding=[Text.UTF8Encoding]::new($false)
$engineLock=$null
try {
    $headers=@{'User-Agent'='Tensura-Managed-Updater';'Cache-Control'='no-cache'}
    if (!$PackUrl) {
        $refs=Invoke-WebRequest -UseBasicParsing -Headers $headers -Uri 'https://github.com/Notatum/Tensura-Pack.git/info/refs?service=git-upload-pack'
        $refText=if($refs.Content -is [byte[]]){[Text.Encoding]::ASCII.GetString($refs.Content)}else{[string]$refs.Content}
        $release=[regex]::Match($refText,'([0-9a-f]{40}) refs/heads/main(?:\x00|\n)').Groups[1].Value
        if ($release -notmatch '^[0-9a-f]{40}$') { throw 'No se pudo identificar la publicación de Tensura.' }
        $PackUrl="https://raw.githubusercontent.com/Notatum/Tensura-Pack/$release/pack.toml"
    } else {
        if (([uri]$PackUrl).Scheme -notin @('http','https')) { throw 'La URL del pack debe ser HTTP o HTTPS.' }
        $release=$PackUrl
    }
    $cacheRoot=[IO.Path]::GetFullPath($CacheDir).TrimEnd('\')
    $gameRoot=[IO.Path]::GetFullPath($GameDir).TrimEnd('\')
    if ($cacheRoot -eq $gameRoot -or $cacheRoot.StartsWith($gameRoot+'\',[StringComparison]::OrdinalIgnoreCase) -or $gameRoot.StartsWith($cacheRoot+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'La caché debe estar separada del juego.' }
    New-Item -ItemType Directory -Path $cacheRoot -Force | Out-Null
    if ((Get-Item -LiteralPath $cacheRoot -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'La caché no puede ser un enlace de directorio.' }
    $engineLock=[IO.File]::Open((Join-Path $cacheRoot 'engine.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    $manifest=Invoke-RestMethod -UseBasicParsing -Headers $headers -Uri ([uri]::new([uri]$PackUrl,'updater/client/release.json'))
    if ($manifest.schema -ne 1 -or $manifest.entry -ne 'Aplicar-Cliente.ps1' -or !$manifest.files.Count) { throw 'El manifiesto del actualizador no es válido.' }
    $key=if($release -match '^[a-f0-9]{40}$'){$release}else{([BitConverter]::ToString([Security.Cryptography.SHA256]::Create().ComputeHash([Text.Encoding]::UTF8.GetBytes($release))).Replace('-','').ToLowerInvariant())}
    $engine=Join-Path $cacheRoot ('engine\'+$key)
    New-Item -ItemType Directory -Path $engine -Force | Out-Null
    if (@(Get-ChildItem -LiteralPath $engine -Force -Recurse | Where-Object {$_.Attributes -band [IO.FileAttributes]::ReparsePoint}).Count) { throw 'El motor del actualizador contiene enlaces.' }
    $names=@{}
    foreach ($file in $manifest.files) {
        if ($file.name -notmatch '^[A-Za-z0-9_-]+\.(ps1|json)$' -or $file.path -notmatch '^updater/client/[A-Za-z0-9_-]+\.(ps1|json)$' -or $file.sha256 -notmatch '^[a-f0-9]{64}$' -or $names.ContainsKey($file.name)) { throw 'Entrada de motor inválida o repetida.' }
        $names[$file.name]=$true
        $target=Join-Path $engine $file.name
        if (!(Test-Path -LiteralPath $target) -or (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $file.sha256) {
            $download=$target+'.download'
            Invoke-WebRequest -UseBasicParsing -Headers $headers -Uri ([uri]::new([uri]$PackUrl,$file.path)) -OutFile $download
            if ((Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash -ne $file.sha256) { throw "La descarga del motor está alterada: $($file.name)" }
            Move-Item -LiteralPath $download -Destination $target -Force
        }
    }
    foreach ($required in @('Aplicar-Cliente.ps1','Preparar-Runtime.ps1','perfil-tensura.json','Bootstrap.ps1')) { if (!$names.ContainsKey($required)) { throw "Falta un componente del actualizador: $required" } }
    [IO.File]::WriteAllText((Join-Path $engine 'release.json'),($manifest | ConvertTo-Json -Depth 30),[Text.UTF8Encoding]::new($false))
    # This stable entry point also receives future bootstrap corrections.
    $self=Join-Path $PSScriptRoot 'Actualizar-Tensura-Cliente.ps1'
    $newSelf=Join-Path $engine 'Bootstrap.ps1'
    if ((Split-Path -Leaf $PSCommandPath) -eq 'Actualizar-Tensura-Cliente.ps1' -and (Get-FileHash -LiteralPath $self).Hash -ne (Get-FileHash -LiteralPath $newSelf).Hash) {
        Copy-Item -LiteralPath $self -Destination ($self+'.previous') -Force
        Copy-Item -LiteralPath $newSelf -Destination $self -Force
    }
    # The native launcher owns its Java/runtime and installs into its own game
    # directory. -NoLaunch remains the noninteractive update protocol it uses.
    if (!$NoLaunch -and $env:TENSURA_NO_LAUNCH -ne '1' -and $manifest.scriptLauncher) {
        $application=$manifest.scriptLauncher
        if ($application.path -ne 'updater/launcher/TensuraLauncher.ps1' -or $application.sha256 -notmatch '^[a-f0-9]{64}$') { throw 'La publicación del launcher no es válida.' }
        $launcherRoot=[IO.Path]::GetFullPath($LauncherDataRoot).TrimEnd('\')
        if ($launcherRoot -eq [IO.Path]::GetPathRoot($launcherRoot).TrimEnd('\')) { throw 'La carpeta del launcher no puede ser la raíz de un disco.' }
        $appRoot=Join-Path $launcherRoot ('app\scripts\'+$key)
        New-Item -ItemType Directory -Path $appRoot -Force | Out-Null
        if ((Get-Item -LiteralPath $launcherRoot -Force).Attributes -band [IO.FileAttributes]::ReparsePoint -or @(Get-ChildItem -LiteralPath (Join-Path $launcherRoot 'app') -Force -Recurse | Where-Object {$_.Attributes -band [IO.FileAttributes]::ReparsePoint}).Count) { throw 'La carpeta del launcher contiene enlaces.' }
        $launcherManifest=Invoke-RestMethod -UseBasicParsing -Headers $headers -Uri ([uri]::new([uri]$PackUrl,'updater/launcher/release.json'))
        if ($launcherManifest.schema -ne 1 -or !$launcherManifest.files.Count) { throw 'El manifiesto del launcher no es válido.' }
        $launcherNames=@{}
        foreach($file in $launcherManifest.files){
            if ($file.name -notmatch '^[A-Za-z0-9_-][A-Za-z0-9_.-]*\.(ps1|cs|json)$' -or $file.path -notmatch '^updater/(launcher|client)/[A-Za-z0-9_-][A-Za-z0-9_.-]*\.(ps1|cs|json)$' -or $file.sha256 -notmatch '^[a-f0-9]{64}$' -or $launcherNames.ContainsKey($file.name)) { throw 'Componente del launcher inválido o repetido.' }
            $launcherNames[$file.name]=$file.sha256
            $target=Join-Path $appRoot $file.name
            if (!(Test-Path -LiteralPath $target) -or (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $file.sha256) {
                $download=$target+'.download'
                Invoke-WebRequest -UseBasicParsing -Headers $headers -Uri ([uri]::new([uri]$PackUrl,$file.path)) -OutFile $download
                if ((Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash -ne $file.sha256) { throw 'Un componente del launcher está alterado.' }
                Move-Item -LiteralPath $download -Destination $target -Force
            }
        }
        foreach($required in @('TensuraLauncher.ps1','Launcher-Ui.cs','Launcher-Engine.ps1','CpuBudget.ps1','AssetStore.cs','Actualizar-Tensura-Cliente.ps1')){if(!$launcherNames.ContainsKey($required)){throw "Falta un componente del launcher: $required"}}
        if ($launcherNames['TensuraLauncher.ps1'] -ne $application.sha256) { throw 'La entrada del launcher no coincide con el manifiesto del cliente.' }
        [IO.File]::WriteAllText((Join-Path $appRoot 'release.json'),($launcherManifest | ConvertTo-Json -Depth 30),[Text.UTF8Encoding]::new($false))
        $launcherScript=Join-Path $appRoot 'TensuraLauncher.ps1'
        if ($PrepareLauncherOnly) { Write-Host ('TENSURA_LAUNCHER_READY|'+$launcherScript); exit 0 }
        $initialAction=if($Mode -eq 'Strict' -or $Action -eq 'repair'){'Repair'}else{'Update'}
        $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName='powershell.exe';$info.Arguments='-NoProfile -STA -ExecutionPolicy Bypass -File "'+$launcherScript+'" -DataRoot "'+$launcherRoot+'" -Action '+$initialAction;$info.UseShellExecute=$false;$info.CreateNoWindow=$true
        [void][Diagnostics.Process]::Start($info)
        Write-Host '[Tensura] Abriendo el launcher independiente para actualizar el cliente.'
        exit 0
    }
    if ($PrepareLauncherOnly) { throw 'La publicación no ofrece un launcher de scripts.' }
    Write-Host "[Tensura] Motor administrado verificado: $release"
    $engineLock.Dispose();$engineLock=$null
    & (Join-Path $engine $manifest.entry) -Action $Action -Mode $Mode -GameDir $GameDir -CacheDir $CacheDir -PackUrl $PackUrl -ReleaseId $release -NoLaunch:$NoLaunch
    exit $LASTEXITCODE
} catch {
    Write-Host "[Tensura] ERROR: $($_.Exception.Message)"
    exit 1
} finally { if($engineLock){$engineLock.Dispose()} }
