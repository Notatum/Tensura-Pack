param(
    [Parameter(Position=0)][ValidateSet('', 'repair')][string]$Action = '',
    [ValidateSet('Incremental', 'Strict')][string]$Mode = 'Incremental',
    [string]$GameDir = (Join-Path $env:APPDATA '.tlauncher\legacy\Minecraft\game'),
    [string]$CacheDir = (Join-Path $env:LOCALAPPDATA 'TensuraUpdater'),
    [string]$PackUrl = '',
    [switch]$NoLaunch,
    [string]$ReleaseId=''
)
$TensuraManagedEngine=$true
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$OutputEncoding = [Text.UTF8Encoding]::new($false)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$folders = @('mods','config','defaultconfigs','resourcepacks','shaderpacks','kubejs','scripts')
$documents = @('GUIA-HABILIDADES.md','REVISION-2026-11-02.md')
$lock = $null
$backup = $null
$moved = [Collections.Generic.List[object]]::new()
$installed = [Collections.Generic.List[string]]::new()
$savedFiles = [Collections.Generic.List[object]]::new()
$touchedFiles = [Collections.Generic.List[string]]::new()
$synchronizing = $false

function Assert-Child([string]$Path, [string]$Root) {
    $resolved = [IO.Path]::GetFullPath($Path).TrimEnd('\')
    if (!$resolved.StartsWith($Root.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) { throw "Ruta fuera de la instalación: $Path" }
    if (Test-Path -LiteralPath $resolved) {
        $items = @((Get-Item -LiteralPath $resolved -Force))
        if (Test-Path -LiteralPath $resolved -PathType Container) { $items += @(Get-ChildItem -LiteralPath $resolved -Force -Recurse) }
        if (@($items | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) { throw "No se sincronizan enlaces o uniones de directorios: $resolved" }
    }
    return $resolved
}
function Get-TreeHashes([string]$Path) {
    $result = @{}
    if (!(Test-Path -LiteralPath $Path -PathType Container)) { return $result }
    $root = [IO.Path]::GetFullPath($Path).TrimEnd('\')
    foreach ($file in (Get-ChildItem -LiteralPath $root -File -Recurse -Force)) { $result[$file.FullName.Substring($root.Length+1)] = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash }
    return $result
}
function Same-Tree([string]$First, [string]$Second) {
    $left = Get-TreeHashes $First; $right = Get-TreeHashes $Second
    if ($left.Count -ne $right.Count) { return $false }
    foreach ($key in $left.Keys) { if (!$right.ContainsKey($key) -or $left[$key] -ne $right[$key]) { return $false } }
    return $true
}
function Desired-Hashes([string]$Folder) {
    $result = Get-TreeHashes (Join-Path $stage $Folder)
    if ($Folder -in @('resourcepacks','shaderpacks')) {
        $local = Get-TreeHashes (Join-Path $gameRoot $Folder)
        foreach ($key in $local.Keys) {
            if (!$result.ContainsKey($key) -and $key -notlike 'Tensura-Espanol-*.zip') { $result[$key]=$local[$key] }
        }
    }
    return $result
}
function Matches-Hashes([hashtable]$Expected, [string]$Path) {
    $actual = Get-TreeHashes $Path
    if ($Expected.Count -ne $actual.Count) { return $false }
    foreach ($key in $Expected.Keys) { if (!$actual.ContainsKey($key) -or $actual[$key] -ne $Expected[$key]) { return $false } }
    return $true
}
function Move-Directory([string]$Source, [string]$Destination) {
    $null = Assert-Child $Source $gameRoot
    $null = Assert-Child $Destination $gameRoot
    for ($attempt=0; $attempt -lt 10; $attempt++) {
        if (Test-Path -LiteralPath $Destination) { throw "El destino ya existe: $Destination" }
        try { [IO.Directory]::Move($Source, $Destination); return }
        catch [IO.IOException] { if ($attempt -eq 9) { throw }; Start-Sleep -Milliseconds (100 + $attempt*50) }
    }
}
function Run-Java([string[]]$Arguments, [string]$Directory) {
    foreach ($argument in $Arguments) { if ($argument.Contains('"')) { throw 'Argumento Java no válido.' } }
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $java
    $info.Arguments = (($Arguments | ForEach-Object { '"' + $_ + '"' }) -join ' ')
    $info.WorkingDirectory = $Directory
    $info.UseShellExecute = $false; $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = [Text.UTF8Encoding]::new($false); $info.StandardErrorEncoding = [Text.UTF8Encoding]::new($false)
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $info
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
        if (!$process.WaitForExit(420000)) { $process.Kill(); throw 'Se agotó el tiempo de espera de Packwiz.' }
        $output = $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult()
        if ($output) { Write-Host $output.TrimEnd() }
        if ($process.ExitCode -ne 0) { throw "Packwiz terminó con código $($process.ExitCode)." }
    } finally { $process.Dispose() }
}
function Save-File([string]$Path) {
    $null = Assert-Child $Path $gameRoot
    if (Test-Path -LiteralPath $Path -PathType Leaf) {
        $saved = Join-Path $backup $Path.Substring($gameRoot.Length+1)
        New-Item -ItemType Directory -Path (Split-Path -Parent $saved) -Force | Out-Null
        Copy-Item -LiteralPath $Path -Destination $saved
        $savedFiles.Add(@{ Original=$Path; Saved=$saved })
    }
    $touchedFiles.Add($Path)
}
function Check-ModCache([bool]$Repair) {
    $modsRoot = Join-Path $stage 'mods'
    foreach ($name in $expectedMods.Keys) {
        $file = Assert-Child (Join-Path $modsRoot $name) $stage
        if (Test-Path -LiteralPath $file -PathType Leaf) {
            if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $expectedMods[$name]) {
                if (!$Repair) { throw "El mod descargado no coincide con el manifiesto publicado: $name" }
                Remove-Item -LiteralPath $file -Force
                Write-Host "[Tensura] Se descargará otra vez el mod de caché alterado: $name"
            }
        } elseif (!$Repair) { throw "Falta el mod publicado: $name" }
    }
    if (!$Repair) {
        $actual = @(Get-ChildItem -LiteralPath $modsRoot -File -Recurse -Force)
        if ($actual.Count -ne $expectedMods.Count -or @($actual | Where-Object { !$expectedMods.ContainsKey($_.Name) -or $_.DirectoryName -ne $modsRoot }).Count) {
            throw 'La caché contiene archivos de mods ajenos al manifiesto publicado.'
        }
    }
}
function Find-Java21 {
    $candidates=@()
    $command=Get-Command java.exe -ErrorAction SilentlyContinue
    if($command){$candidates+=@($command.Source)}
    $candidates+=@(Join-Path $env:APPDATA '.tlauncher\legacy\Minecraft\jre\x64\bin\java.exe')
    foreach($candidate in ($candidates | Select-Object -Unique)){
        if(!(Test-Path -LiteralPath $candidate)){continue}
        $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=$candidate;$info.Arguments='-version';$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardError=$true;$info.RedirectStandardOutput=$true
        $process=[Diagnostics.Process]::Start($info)
        try{$errorText=$process.StandardError.ReadToEnd();$outputText=$process.StandardOutput.ReadToEnd();$process.WaitForExit();if(($errorText+$outputText) -match 'version "21[.\"]'){return $candidate}}finally{$process.Dispose()}
    }
    throw 'Se necesita Java 21. Instalá Java 21 o el runtime recomendado de Legacy Launcher y volvé a ejecutar el updater.'
}
try {
    if (!(Test-Path -LiteralPath $GameDir -PathType Container)) {
        $requested=[IO.Path]::GetFullPath($GameDir).TrimEnd('\')
        if($requested -eq [IO.Path]::GetPathRoot($requested).TrimEnd('\')){throw 'La instalación no puede ser la raíz de un disco.'}
        New-Item -ItemType Directory -Path $requested -Force | Out-Null
    }
    $gameRoot = (Resolve-Path -LiteralPath $GameDir).Path.TrimEnd('\')
    if ($gameRoot -eq [IO.Path]::GetPathRoot($gameRoot).TrimEnd('\')) { throw 'La instalación no puede ser la raíz de un disco.' }
    if ((Get-Item -LiteralPath $gameRoot).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Usá la ruta física de la instalación, sin enlaces de directorio.' }
    $cacheRoot = [IO.Path]::GetFullPath($CacheDir).TrimEnd('\')
    if ($cacheRoot -eq $gameRoot -or $cacheRoot.StartsWith($gameRoot+'\',[StringComparison]::OrdinalIgnoreCase) -or $gameRoot.StartsWith($cacheRoot+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'La caché y la instalación deben estar en carpetas separadas.' }
    foreach ($folder in $folders) { $null = Assert-Child (Join-Path $gameRoot $folder) $gameRoot }
    $running = @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe' OR Name='LL.exe'" | Where-Object { $_.Name -eq 'LL.exe' -or $_.CommandLine -and $_.CommandLine.IndexOf($gameRoot,[StringComparison]::OrdinalIgnoreCase) -ge 0 })
    if ($running.Count) { throw 'Cerrá Minecraft y TL Legacy antes de actualizar esta instalación.' }
    New-Item -ItemType Directory -Path $cacheRoot -Force | Out-Null
    $lock = [IO.File]::Open((Join-Path $cacheRoot 'updater.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    $java = Find-Java21
    $headers = @{ 'User-Agent'='TensuraUpdater'; 'Cache-Control'='no-cache' }
    if (!$PackUrl) {
        $refs = Invoke-WebRequest -UseBasicParsing -Headers $headers -Uri 'https://github.com/Notatum/Tensura-Pack.git/info/refs?service=git-upload-pack'
        $refText = if ($refs.Content -is [byte[]]) { [Text.Encoding]::ASCII.GetString($refs.Content) } else { [string]$refs.Content }
        $main = [regex]::Match($refText,'([0-9a-f]{40}) refs/heads/main(?:\x00|\n)')
        $release = $main.Groups[1].Value
        if ($release -notmatch '^[0-9a-f]{40}$') { throw 'GitHub no devolvió un commit válido.' }
        $PackUrl = "https://raw.githubusercontent.com/Notatum/Tensura-Pack/$release/pack.toml"
    } else {
        if (([uri]$PackUrl).Scheme -notin @('http','https')) { throw 'La URL del pack debe ser HTTP o HTTPS.' }
        $release = if($ReleaseId){$ReleaseId}else{$PackUrl}
    }
    Write-Host "[Tensura] Cliente: $gameRoot"
    Write-Host "[Tensura] Versión: $release"
    $bootstrap = Join-Path $cacheRoot 'packwiz-installer-bootstrap.jar'
    if (!(Test-Path -LiteralPath $bootstrap)) {
        Invoke-WebRequest -UseBasicParsing -Uri 'https://github.com/packwiz/packwiz-installer-bootstrap/releases/download/v0.0.3/packwiz-installer-bootstrap.jar' -OutFile ($bootstrap+'.download')
        Move-Item -LiteralPath ($bootstrap+'.download') -Destination $bootstrap
    }
    $archive = [IO.Compression.ZipFile]::OpenRead($bootstrap)
    try { if (!$archive.GetEntry('META-INF/MANIFEST.MF')) { throw 'El instalador descargado no es un JAR válido.' } } finally { $archive.Dispose() }
    $stage = Join-Path $cacheRoot 'staging'
    New-Item -ItemType Directory -Path $stage -Force | Out-Null
    $null = Assert-Child $stage $cacheRoot
    $installer = Join-Path $stage 'packwiz-installer.jar'
    if (!(Test-Path -LiteralPath $installer)) {
        Invoke-WebRequest -UseBasicParsing -Uri 'https://github.com/packwiz/packwiz-installer/releases/download/v0.5.14/packwiz-installer.jar' -OutFile ($installer+'.download')
        $jar = [IO.Compression.ZipFile]::OpenRead($installer+'.download')
        try { if (!$jar.GetEntry('META-INF/MANIFEST.MF')) { throw 'El instalador de Packwiz no es un JAR válido.' } } finally { $jar.Dispose() }
        Move-Item -LiteralPath ($installer+'.download') -Destination $installer
    }
    $modManifest = Invoke-RestMethod -UseBasicParsing -Headers $headers -Uri ([uri]::new([uri]$PackUrl,'updater/manifests/mods-client.json'))
    if ($modManifest.side -ne 'client' -or !$modManifest.mods.Count) { throw 'El manifiesto publicado de mods de cliente no es válido.' }
    $expectedMods = @{}
    foreach ($entry in $modManifest.mods) {
        if ($entry.file -notmatch '^[^\\/]+\.jar$' -or $entry.sha256 -notmatch '^[a-f0-9]{64}$' -or $expectedMods.ContainsKey($entry.file)) { throw 'El manifiesto contiene un mod inválido o repetido.' }
        $expectedMods[$entry.file] = $entry.sha256
    }
    Check-ModCache $true
    # Packwiz checks the cache every run and downloads only missing or changed files.
    Run-Java @('-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-jar',$bootstrap,'--bootstrap-no-update','-g','-s','client',$PackUrl) $stage
    Check-ModCache $false
    $policy=Get-Content -LiteralPath (Join-Path $stage 'config\tensura-managed-client.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $stageShader=Assert-Child (Join-Path $stage ('shaderpacks\'+$policy.shaderPack)) $stage
    if (!(Test-Path -LiteralPath $stageShader) -or (Get-FileHash -LiteralPath $stageShader -Algorithm SHA256).Hash -ne $policy.shaderSha256) {
        if (Test-Path -LiteralPath $stageShader -PathType Leaf) { Remove-Item -LiteralPath $stageShader -Force }
        Run-Java @('-jar',$bootstrap,'--bootstrap-no-update','-g','-s','client',$PackUrl) $stage
        if (!(Test-Path -LiteralPath $stageShader) -or (Get-FileHash -LiteralPath $stageShader -Algorithm SHA256).Hash -ne $policy.shaderSha256) { throw 'No se pudo reparar el shader administrado.' }
    }
    if (!(Test-Path -LiteralPath (Join-Path $stage 'Configurar-Tensura-Idioma.ps1'))) { throw 'El pack no incluye el configurador de idioma.' }
    if (@(Get-ChildItem -LiteralPath (Join-Path $stage 'mods') -File -Filter '*.jar').Count -eq 0) { throw 'El pack no entregó mods de cliente.' }
    $desired = @{}
    $changes = @(foreach ($folder in $folders) {
        $source = Assert-Child (Join-Path $stage $folder) $stage
        $destination = Join-Path $gameRoot $folder
        $desired[$folder] = Desired-Hashes $folder
        if (!(Matches-Hashes $desired[$folder] $destination) -or ($Mode -eq 'Strict' -or $Action -eq 'repair') -and (Test-Path -LiteralPath $source)) { $folder }
    })
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    $backup = Assert-Child (Join-Path $gameRoot ('.tensura-backups\'+$stamp)) $gameRoot
    $work = Assert-Child (Join-Path $gameRoot ('.tensura-update-work\'+$stamp)) $gameRoot
    New-Item -ItemType Directory -Path $backup,$work -Force | Out-Null
    foreach ($folder in $changes) {
        $source = Join-Path $stage $folder
        if (Test-Path -LiteralPath $source) { Copy-Item -LiteralPath $source -Destination (Join-Path $work $folder) -Recurse }
        if ($folder -in @('resourcepacks','shaderpacks')) {
            foreach ($key in $desired[$folder].Keys) {
                $preparedFile = Assert-Child (Join-Path (Join-Path $work $folder) $key) $work
                if (!(Test-Path -LiteralPath $preparedFile)) {
                    New-Item -ItemType Directory -Path (Split-Path -Parent $preparedFile) -Force | Out-Null
                    Copy-Item -LiteralPath (Join-Path (Join-Path $gameRoot $folder) $key) -Destination $preparedFile
                }
            }
        }
    }
    $synchronizing = $true
    foreach ($folder in $changes) {
        $destination = Join-Path $gameRoot $folder
        if (Test-Path -LiteralPath $destination) {
            $saved = Join-Path $backup $folder
            Move-Directory $destination $saved
            $moved.Add(@{ Original=$destination; Saved=$saved })
        }
        $prepared = Join-Path $work $folder
        if (Test-Path -LiteralPath $prepared) { Move-Directory $prepared $destination; $installed.Add($destination) }
        Write-Host "[Tensura] Sincronizado: $folder"
    }
    foreach ($document in $documents) {
        if (Test-Path -LiteralPath (Join-Path $stage $document)) { $target=Join-Path $gameRoot $document; Save-File $target; Copy-Item -LiteralPath (Join-Path $stage $document) -Destination $target -Force }
    }
    Save-File (Join-Path $gameRoot 'options.txt')
    & (Join-Path $stage 'Configurar-Tensura-Idioma.ps1') -GameDir $gameRoot
    foreach ($folder in $folders) { if (!(Matches-Hashes $desired[$folder] (Join-Path $gameRoot $folder))) { throw "La verificación final no coincide para $folder" } }
    Save-File (Join-Path $gameRoot 'launcher_profiles.json')
    $managedPolicy=& (Join-Path $PSScriptRoot 'Preparar-Runtime.ps1') -GameDir $gameRoot -CacheDir $cacheRoot -Java $java -Template (Join-Path $PSScriptRoot 'perfil-tensura.json') -ReleaseManifest (Join-Path $PSScriptRoot 'release.json')
    $stateFile = Join-Path $gameRoot '.tensura-client-release.json'; Save-File $stateFile
    $state = @{ release=$release; modCount=@(Get-ChildItem -LiteralPath (Join-Path $gameRoot 'mods') -Filter '*.jar' -File).Count; backup=$backup; mode=$Mode; profile=$managedPolicy.profile; mandatoryShaders=$managedPolicy.mandatoryShaders; shaderPack=$managedPolicy.shaderPack; managedEngine=$true }
    [IO.File]::WriteAllText($stateFile,($state | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $cacheRoot 'pack.commit'),$release,[Text.UTF8Encoding]::new($false))
    $synchronizing = $false
    Write-Host "[Tensura] Cliente verificado. Mods: $($state.modCount). Carpetas actualizadas: $($changes.Count). Respaldo: $backup"
    if (!$NoLaunch -and $env:TENSURA_NO_LAUNCH -ne '1') {
        $launcherHome=Join-Path $env:APPDATA '.tlauncher\legacy\Minecraft'
        $launcherJar=Join-Path $launcherHome 'launcher\bootstrap.jar'
        $launcherJava=Join-Path $launcherHome 'jre\x64\bin\java.exe'
        if ((Test-Path -LiteralPath $launcherJar) -and (Test-Path -LiteralPath $launcherJava)) {
            $settings=Join-Path $cacheRoot 'managed-launcher.properties'
            $text=if(Test-Path -LiteralPath (Join-Path $launcherHome 'tl.properties')){[IO.File]::ReadAllText((Join-Path $launcherHome 'tl.properties'))}else{''}
            $values=@{'login.version'=$managedPolicy.profile;'login.auto'='true';'minecraft.gamedir'=$gameRoot.Replace('\','\\').Replace(':','\:');'minecraft.gamedir.separate'='none'}
            foreach($key in $values.Keys){$pattern='(?m)^'+[regex]::Escape($key)+'=[^\r\n]*';$line=$key+'='+$values[$key];if($text -match $pattern){$text=[regex]::Replace($text,$pattern,[Text.RegularExpressions.MatchEvaluator]{param($m)$line})}else{$text=$text.TrimEnd()+"`n"+$line+"`n"}}
            [IO.File]::WriteAllText($settings,$text,[Text.UTF8Encoding]::new($false))
            $arguments=@('-Xmx256m','-Dfile.encoding=UTF-8','-Djava.net.useSystemProxies=true','-Dtlauncher.systemCharset=UTF-8','--add-exports','java.desktop/sun.awt=javafx.swing','--add-exports','javafx.graphics/com.sun.javafx.application=ALL-UNNAMED','-Dloader.main=net.legacylauncher.bootstrap.Bootstrap','-classpath',$launcherJar,'org.springframework.boot.loader.PropertiesLauncher','--packageMode','windows','--targetJar','launcher/launcher.jar','--targetLibFolder','launcher/libraries','--','--settings',$settings)
            $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=$launcherJava;$info.Arguments=(($arguments | ForEach-Object{'"'+$_+'"'}) -join ' ');$info.WorkingDirectory=$launcherHome;$info.UseShellExecute=$false;$info.CreateNoWindow=$true
            [void][Diagnostics.Process]::Start($info)
            Write-Host '[Tensura] Iniciando el perfil administrado con Unbound obligatorio.'
        } else { Write-Host "[Tensura] Cliente listo. En tu launcher usá el perfil $($managedPolicy.profile)." }
    }
    exit 0
} catch {
    $failure = $_
    if ($synchronizing) {
        try {
            foreach ($path in $installed) { $safe=Assert-Child $path $gameRoot; if (Test-Path -LiteralPath $safe) { Remove-Item -LiteralPath $safe -Recurse -Force } }
            foreach ($entry in $moved) { Move-Directory $entry.Saved $entry.Original }
            foreach ($path in $touchedFiles) { $safe=Assert-Child $path $gameRoot; if (Test-Path -LiteralPath $safe -PathType Leaf) { Remove-Item -LiteralPath $safe -Force } }
            foreach ($entry in $savedFiles) { Copy-Item -LiteralPath $entry.Saved -Destination $entry.Original -Force }
            Write-Host '[Tensura] Se restauró la instalación anterior.'
        } catch { Write-Host "[Tensura] Revisá el respaldo antes de iniciar Minecraft: $backup. $($_.Exception.Message)" }
    }
    Write-Host "[Tensura] ERROR: $($failure.Exception.Message)"
    exit 1
} finally { if ($lock) { $lock.Dispose() } }
