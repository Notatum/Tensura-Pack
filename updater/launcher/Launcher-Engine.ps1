param(
    [Parameter(Mandatory=$true)][string]$DataRoot,
    [Parameter(Mandatory=$true)][string]$PlayerName,
    [ValidateRange(2048,16384)][int]$MemoryMb=6144,
    [ValidateRange(0,64)][int]$CpuCount=0,
    [ValidateRange(1,100)][int]$CpuPercent=80,
    [ValidateSet('Play','Update','Repair','Recipe')][string]$Action='Play',
    [Parameter(Mandatory=$true)][string]$PackUrl,
    [Parameter(Mandatory=$true)][string]$ClientBootstrap,
    [Parameter(Mandatory=$true)][string]$LauncherManifest,
    [string]$ImportSource=(Join-Path $env:APPDATA '.tlauncher\legacy\Minecraft\game'),
    [string[]]$ExtraGameArguments=@()
)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
$OutputEncoding=[Text.UTF8Encoding]::new($false)
[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -Path (Join-Path $PSScriptRoot 'AssetStore.cs') -ReferencedAssemblies System.Web.Extensions,System.Core
. (Join-Path $PSScriptRoot 'CpuBudget.ps1')
if($PlayerName -notmatch '^[A-Za-z0-9_]{3,16}$'){throw 'El nombre debe tener entre 3 y 16 letras, números o guiones bajos.'}
$root=[IO.Path]::GetFullPath($DataRoot).TrimEnd('\')
if($root -eq [IO.Path]::GetPathRoot($root).TrimEnd('\')){throw 'La carpeta del launcher no puede ser la raíz de un disco.'}
$game=Join-Path $root 'game';$cache=Join-Path $root 'cache';$logs=Join-Path $root 'logs'
$settingsPath=Join-Path $root 'settings.json'
if(Test-Path -LiteralPath $settingsPath){
    $cpuSettings=Get-Content -LiteralPath $settingsPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if(!$PSBoundParameters.ContainsKey('CpuCount') -and $null -ne $cpuSettings.CpuCount){$CpuCount=[int]$cpuSettings.CpuCount}
    if(!$PSBoundParameters.ContainsKey('CpuPercent') -and $cpuSettings.CpuPercent){$CpuPercent=[int]$cpuSettings.CpuPercent}
}
$cpuPlan=Get-TensuraCpuPlan -CpuCount $CpuCount -CpuPercent $CpuPercent
New-Item -ItemType Directory -Path $root,$game,$cache,$logs -Force | Out-Null
if((Get-Item -LiteralPath $root -Force).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'La carpeta de datos no puede ser un enlace.'}
function Status([string]$Message){Write-Host ('TENSURA_STATUS|'+$Message)}
function Check-Cancel {if(Test-Path -LiteralPath (Join-Path $cache 'cancel.request')){throw 'Operación cancelada. Los archivos preparados se conservaron.'}}
function Write-PhaseTime([string]$Name,[Diagnostics.Stopwatch]$Timer){
    # Times are milliseconds; no launch arguments or personal data are logged.
    Write-Host ('TENSURA_TIMING|launcher|'+$Name+'|'+$Timer.ElapsedMilliseconds)
    $Timer.Restart()
}
function Prepare-LauncherUpgrade {
    # Bridge for the already distributed 1.2 EXE: its verified engine can
    # prepare the new version, which is applied after that launcher closes.
    # A script-hosted UI and unrelated parent processes are never replaced.
    try {
        $worker=Get-CimInstance Win32_Process -Filter "ProcessId=$PID"
        $parent=Get-Process -Id $worker.ParentProcessId -ErrorAction Stop
        $target=$parent.Path
        if(!$target -or [IO.Path]::GetExtension($target) -ne '.exe'){return}
        $info=[Diagnostics.FileVersionInfo]::GetVersionInfo($target)
        if($info.ProductName -ne 'Tensura Launcher'){return}
        $release=Get-Content -LiteralPath $LauncherManifest -Raw -Encoding UTF8 | ConvertFrom-Json
        if([version]$release.version -le [version]$info.FileVersion){return}
        $arguments=@('-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File',(Join-Path $PSScriptRoot 'Launcher-Update.ps1'),'-Target',$target,'-DataRoot',$root,'-Manifest',$LauncherManifest,'-PackUrl',$PackUrl,'-WaitPid',[string]$parent.Id)
        $result=& powershell.exe @arguments 2>&1
        if($LASTEXITCODE -eq 0 -and ($result -join "`n") -match 'TENSURA_LAUNCHER_UPDATE_READY'){
            Status 'Nueva versión del launcher preparada; se aplicará al cerrar esta ventana.'
        } elseif($LASTEXITCODE -ne 0){Write-Host '[Tensura] No se pudo preparar la actualización del launcher. Revisá logs/launcher-update.log.'}
    } catch { Write-Host ('[Tensura] La actualización del launcher se reintentará: '+$_.Exception.Message) }
}
function Safe-Child([string]$Base,[string]$Relative){
    $path=[IO.Path]::GetFullPath((Join-Path $Base $Relative))
    if(!$path.StartsWith($Base.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Se rechazó una ruta fuera de la instalación.'}
    return $path
}
function Get-EntryHash($Entry){
    $sha=[Security.Cryptography.SHA256]::Create();$stream=$null
    try{$stream=$Entry.Open();return [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-','')}
    finally{if($stream){$stream.Dispose()};$sha.Dispose()}
}
function Sync-NativeLibraries([string[]]$Jars,[string]$Destination){
    $kept=0;$written=0
    foreach($jar in $Jars){
        $zip=[IO.Compression.ZipFile]::OpenRead($jar)
        try{
            foreach($entry in $zip.Entries){
                if($entry.FullName.StartsWith('META-INF/') -or !$entry.FullName.EndsWith('.dll')){continue}
                Check-Cancel
                $target=Safe-Child $Destination ([IO.Path]::GetFileName($entry.FullName))
                # The containing JAR was verified above; compare bytes, not mtime.
                $expected=Get-EntryHash $entry
                if((Test-Path -LiteralPath $target -PathType Leaf) -and (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -eq $expected){$kept++;continue}
                $temp=$target+'.extract-'+[Guid]::NewGuid().ToString('N')
                try{
                    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$temp,$false)
                    if((Get-FileHash -LiteralPath $temp -Algorithm SHA256).Hash -ne $expected){throw 'Una biblioteca nativa extraída no coincide con su origen verificado.'}
                    if(Test-Path -LiteralPath $target){[IO.File]::Replace($temp,$target,[NullString]::Value)}else{[IO.File]::Move($temp,$target)}
                    $written++
                }finally{if(Test-Path -LiteralPath $temp){[IO.File]::Delete($temp)}}
            }
        }finally{$zip.Dispose()}
    }
    return @{kept=$kept;written=$written}
}
function Copy-NewTree([string]$Source,[string]$Destination){
    if(!(Test-Path -LiteralPath $Source -PathType Container)){return}
    $pending=[Collections.Generic.Stack[string]]::new();$pending.Push($Source)
    while($pending.Count){
      Check-Cancel
      $directory=$pending.Pop()
      foreach($file in (Get-ChildItem -LiteralPath $directory -Force)){
        if($file.Attributes -band [IO.FileAttributes]::ReparsePoint){continue}
        if($file.PSIsContainer){$pending.Push($file.FullName);continue}
        $relative=$file.FullName.Substring($Source.TrimEnd('\').Length+1)
        $target=Safe-Child $Destination $relative
        if(!(Test-Path -LiteralPath $target)){
            New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
            Copy-Item -LiteralPath $file.FullName -Destination $target
        }
      }
    }
}
function Is-Java21([string]$Executable){
    if(!(Test-Path -LiteralPath $Executable)){return $false}
    $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=$Executable;$info.Arguments='-version';$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    $p=[Diagnostics.Process]::Start($info)
    try{$text=$p.StandardError.ReadToEnd()+$p.StandardOutput.ReadToEnd();$p.WaitForExit();return $text -match 'version "21[.\"]'}finally{$p.Dispose()}
}
function Get-OwnedJava {
    $owned=Join-Path $root 'runtime\java';$executable=Join-Path $owned 'bin\java.exe'
    if(Is-Java21 $executable){return $executable}
    $candidates=@(Join-Path (Split-Path -Parent $ImportSource) 'jre\x64\bin\java.exe')
    $systemJava=Get-Command java.exe -ErrorAction SilentlyContinue
    if($systemJava){$candidates+=@($systemJava.Source)}
    foreach($candidate in $candidates){
        if(Is-Java21 $candidate){
            Status 'Reutilizando Java 21 en la carpeta independiente'
            $source=Split-Path -Parent (Split-Path -Parent $candidate)
            Copy-NewTree $source $owned
            if(Is-Java21 $executable){return $executable}
        }
    }
    Status 'Descargando Java 21 verificado'
    $release=Get-Content -LiteralPath $LauncherManifest -Raw -Encoding UTF8 | ConvertFrom-Json
    $archive=Join-Path $cache 'java21.zip'
    [TensuraAssetStore]::Ensure($release.runtime.url,$archive,$release.runtime.sha256,$null)
    $zip=[IO.Compression.ZipFile]::OpenRead($archive)
    try{
        foreach($entry in $zip.Entries){
            if($entry.FullName.EndsWith('/')){continue}
            $segments=$entry.FullName.Split('/')
            if($segments.Length -lt 2){continue}
            $relative=($segments | Select-Object -Skip 1) -join '\'
            $target=Safe-Child $owned $relative
            New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$target,$true)
        }
    }finally{$zip.Dispose()}
    if(!(Is-Java21 $executable)){throw 'No se pudo preparar el runtime de Java 21.'}
    return $executable
}
function Rule-Matches($Rule){
    if($Rule.os){
        if($Rule.os.name -and $Rule.os.name -ne 'windows'){return $false}
        if($Rule.os.arch -and $Rule.os.arch -notin @('amd64','x86_64')){return $false}
        if($Rule.os.version -and [Environment]::OSVersion.Version.ToString() -notmatch $Rule.os.version){return $false}
    }
    if($Rule.features){
        foreach($property in $Rule.features.PSObject.Properties){
            $enabled=$property.Name -eq 'has_custom_resolution'
            if([bool]$property.Value -ne $enabled){return $false}
        }
    }
    return $true
}
function Allowed($Rules){
    if(!$Rules -or !$Rules.Count){return $true}
    $allowed=$false
    foreach($rule in $Rules){if(Rule-Matches $rule){$allowed=$rule.action -eq 'allow'}}
    return $allowed
}
function Maven-Path([string]$Name){
    if($Name -notmatch '^[A-Za-z0-9_.+-]+:[A-Za-z0-9_.+-]+:[A-Za-z0-9_.+-]+(:[A-Za-z0-9_.+-]+)?$'){throw "Coordenada de biblioteca inválida: $Name"}
    $parts=$Name.Split(':');$file=$parts[1]+'-'+$parts[2]
    if($parts.Length -eq 4){$file+='-'+$parts[3]}
    return $parts[0].Replace('.','/')+'/'+$parts[1]+'/'+$parts[2]+'/'+$file+'.jar'
}
function Expand-Arguments($Arguments,$Variables){
    $result=[Collections.Generic.List[string]]::new()
    foreach($argument in $Arguments){
        if($argument -is [string]){$values=@($argument)}else{if(!(Allowed $argument.rules)){continue};$values=@($argument.value)}
        foreach($value in $values){
            $expanded=[regex]::Replace([string]$value,'\$\{([^}]+)\}',[Text.RegularExpressions.MatchEvaluator]{param($m)if(!$Variables.ContainsKey($m.Groups[1].Value)){throw "Variable del perfil desconocida: $($m.Groups[1].Value)"};[string]$Variables[$m.Groups[1].Value]})
            $result.Add($expanded)
        }
    }
    return $result.ToArray()
}
function Offline-UUID([string]$Name){
    $md5=[Security.Cryptography.MD5]::Create()
    try{$bytes=$md5.ComputeHash([Text.Encoding]::UTF8.GetBytes('OfflinePlayer:'+$Name))}finally{$md5.Dispose()}
    $bytes[6]=($bytes[6] -band 15) -bor 48;$bytes[8]=($bytes[8] -band 63) -bor 128
    $hex=[BitConverter]::ToString($bytes).Replace('-','').ToLowerInvariant()
    return $hex.Substring(0,8)+'-'+$hex.Substring(8,4)+'-'+$hex.Substring(12,4)+'-'+$hex.Substring(16,4)+'-'+$hex.Substring(20)
}

$sessionLock=$null
$taskTimer=[Diagnostics.Stopwatch]::StartNew()
$phaseTimer=[Diagnostics.Stopwatch]::StartNew()
try{
    $sessionLock=[IO.File]::Open((Join-Path $root 'launcher-task.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    Prepare-LauncherUpgrade
    $pidFile=Join-Path $root 'game-session.json'
    if(Test-Path -LiteralPath $pidFile){
        $previous=Get-Content -LiteralPath $pidFile -Raw -Encoding UTF8 | ConvertFrom-Json
        $running=Get-Process -Id $previous.pid -ErrorAction SilentlyContinue
        if($running -and $running.StartTime.ToUniversalTime().ToString('o') -eq $previous.started){throw 'Minecraft ya está abierto en esta instalación.'}
    }
    $java=Get-OwnedJava
    Write-PhaseTime 'launcher-java' $phaseTimer
    Check-Cancel
    $env:JAVA_HOME=Split-Path -Parent (Split-Path -Parent $java)
    $env:PATH=(Split-Path -Parent $java)+';'+$env:PATH
    if(!(Test-Path -LiteralPath (Join-Path $root 'import-complete.json'))){
        if(Test-Path -LiteralPath $ImportSource -PathType Container){
            if([IO.Path]::GetFullPath($ImportSource).TrimEnd('\') -eq $game){throw 'La instalación de origen debe ser diferente de la nueva.'}
            Status 'Reutilizando archivos y copiando tus partidas a la instalación independiente'
            foreach($folder in @('libraries','mods','config','resourcepacks','shaderpacks','saves','screenshots','xaero','orbe_audio','greatsage-voice')){Copy-NewTree (Join-Path $ImportSource $folder) (Join-Path $game $folder)}
            foreach($file in @('options.txt','servers.dat','servers.dat_old')){if((Test-Path -LiteralPath (Join-Path $ImportSource $file)) -and !(Test-Path -LiteralPath (Join-Path $game $file))){Copy-Item -LiteralPath (Join-Path $ImportSource $file) -Destination (Join-Path $game $file)}}
            foreach($version in @('1.21.1','Tensura-1.21.1-NeoForge-21.1.250')){Copy-NewTree (Join-Path $ImportSource ('versions\'+$version)) (Join-Path $game ('versions\'+$version))}
        }
        [IO.File]::WriteAllText((Join-Path $root 'import-complete.json'),'{"completed":true}',[Text.UTF8Encoding]::new($false))
    }
    Status 'Comprobando el cliente: actualización incremental por archivo'
    $updaterCache=Join-Path $cache 'updater'
    if(!(Test-Path -LiteralPath (Join-Path $updaterCache 'staging'))){
        $previousCache=Join-Path $env:LOCALAPPDATA 'TensuraUpdater\staging'
        if(Test-Path -LiteralPath $previousCache -PathType Container){Copy-NewTree $previousCache (Join-Path $updaterCache 'staging')}
    }
    $updateMode=if($Action -eq 'Repair'){'Strict'}else{'Incremental'}
    $updaterArgs=@('-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File',$ClientBootstrap,'-GameDir',$game,'-CacheDir',$updaterCache,'-PackUrl',$PackUrl,'-Mode',$updateMode,'-NoLaunch')
    Write-PhaseTime 'importacion' $phaseTimer
    & powershell.exe @updaterArgs
    if($LASTEXITCODE -ne 0){throw 'La actualización del cliente no se completó. Revisá el registro.'}
    Write-PhaseTime 'updater' $phaseTimer
    Check-Cancel
    $state=Get-Content -LiteralPath (Join-Path $game '.tensura-client-release.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $policy=Get-Content -LiteralPath (Join-Path $game 'config\tensura-managed-client.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $profile=Get-Content -LiteralPath (Join-Path $game ('versions\'+$policy.profile+'\'+$policy.profile+'.json')) -Raw -Encoding UTF8 | ConvertFrom-Json
    $libraryRoot=Join-Path $game 'libraries';$classpath=[Collections.Generic.List[string]]::new();$natives=[Collections.Generic.List[string]]::new()
    Status 'Verificando bibliotecas de Minecraft'
    foreach($library in $profile.libraries){
        Check-Cancel
        if(!(Allowed $library.rules)){continue}
        $artifact=$library.downloads.artifact
        if(!$artifact){continue}
        $relative=if($artifact.path){$artifact.path}else{Maven-Path $library.name}
        $target=Safe-Child $libraryRoot $relative
        if($artifact.url -match '^https?://'){
            [TensuraAssetStore]::Ensure($artifact.url,$target,$artifact.sha1,(Join-Path $ImportSource ('libraries\'+$relative)))
        }elseif(!(Test-Path -LiteralPath $target)){throw "Falta una biblioteca generada por NeoForge: $($library.name). Usá Reparar."}
        if(!$library.downloadOnly){$classpath.Add($target)}
        if($library.name -match ':natives-windows$'){$natives.Add($target)}
    }
    $profileJar=Join-Path $game ('versions\'+$policy.profile+'\'+$policy.profile+'.jar')
    $classpath.Add($profileJar)
    $nativeDir=Join-Path $game ('natives\'+$policy.profile+'-x64')
    New-Item -ItemType Directory -Path $nativeDir -Force | Out-Null
    Write-PhaseTime 'bibliotecas' $phaseTimer
    $nativeResult=Sync-NativeLibraries $natives.ToArray() $nativeDir
    Status ('Bibliotecas nativas: '+$nativeResult.kept+' conservadas; '+$nativeResult.written+' nuevas o reparadas.')
    Write-PhaseTime 'nativas' $phaseTimer
    Status 'Verificando los recursos del juego'
    $assets=Join-Path $game 'assets';$index=Join-Path $assets ('indexes\'+$profile.assetIndex.id+'.json')
    [TensuraAssetStore]::Ensure($profile.assetIndex.url,$index,$profile.assetIndex.sha1,(Join-Path $ImportSource ('assets\indexes\'+$profile.assetIndex.id+'.json')))
    [TensuraAssetStore]::Assets($index,$assets,(Join-Path $ImportSource 'assets'))
    Write-PhaseTime 'recursos' $phaseTimer
    Check-Cancel
    $variables=@{auth_player_name=$PlayerName;version_name=$policy.profile;game_directory=$game;assets_root=$assets;assets_index_name=$profile.assetIndex.id;auth_uuid=(Offline-UUID $PlayerName);auth_access_token='0';clientid='';auth_xuid='';user_type='legacy';version_type='Tensura';resolution_width='1280';resolution_height='720';natives_directory=$nativeDir;launcher_name='TensuraLauncher';launcher_version='1.0.0';classpath=($classpath -join ';');library_directory=$libraryRoot;classpath_separator=';'}
    $jvm=@('-Xms512m',('-Xmx'+$MemoryMb+'m'),'-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8')+@(Expand-Arguments $profile.arguments.jvm $variables | Where-Object {$_ -notmatch '^-XX:ActiveProcessorCount='})+@('-XX:ActiveProcessorCount='+$cpuPlan.count)
    if($profile.logging.client){
        $logging=$profile.logging.client.file;$file=Join-Path $assets ('log_configs\'+$logging.id)
        [TensuraAssetStore]::Ensure($logging.url,$file,$logging.sha1,(Join-Path $ImportSource ('assets\log_configs\'+$logging.id)))
        $jvm+=@($profile.logging.client.argument.Replace('${path}',$file))
    }
    $arguments=$jvm+@($profile.mainClass)+@(Expand-Arguments $profile.arguments.game $variables)+$ExtraGameArguments
    $argfile=Join-Path $cache 'minecraft-arguments.txt'
    $lines=@($arguments | ForEach-Object {'"'+([string]$_).Replace('\','\\').Replace('"','\"')+'"'})
    [IO.File]::WriteAllLines($argfile,$lines,[Text.UTF8Encoding]::new($false))
    $recipe=@{java=$java;argumentsFile=$argfile;gameDirectory=$game;player=$PlayerName;uuid=$variables.auth_uuid;profile=$policy.profile;release=$state.release;libraryCount=$classpath.Count;nativeCount=$natives.Count;mandatoryShaders=$policy.mandatoryShaders;shaderPack=$policy.shaderPack;usesThirdPartyLauncher=$false}
    $recipe.cpuCount=$cpuPlan.count;$recipe.availableProcessors=$cpuPlan.available;$recipe.cpuMode=$cpuPlan.mode;$recipe.cpuPercent=$cpuPlan.percent;$recipe.cpuAffinityMask=$cpuPlan.maskHex
    Status ('CPU: '+$cpuPlan.count+' de '+$cpuPlan.available+' procesadores lógicos ('+$cpuPlan.mode+').')
    [IO.File]::WriteAllText((Join-Path $root 'launch-recipe.json'),($recipe | ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
    Write-PhaseTime 'receta' $phaseTimer
    Write-Host ('TENSURA_TIMING|launcher|preparacion-total|'+$taskTimer.ElapsedMilliseconds)
    if($Action -ne 'Play'){Status 'Cliente independiente actualizado y listo';Write-Host 'TENSURA_READY';exit 0}
    Check-Cancel
    Status 'Iniciando Minecraft directamente'
    $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=$java;$info.Arguments='"@'+$argfile+'"';$info.WorkingDirectory=$game;$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    $p=Start-TensuraCpuProcess -StartInfo $info -Plan $cpuPlan
    Write-PhaseTime 'inicio-java' $phaseTimer
    $session=@{pid=$p.Id;started=$p.StartTime.ToUniversalTime().ToString('o');player=$PlayerName;profile=$policy.profile}
    [IO.File]::WriteAllText($pidFile,($session | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
    $outputFile=[IO.File]::Create((Join-Path $logs 'minecraft-output.log'));$errorFile=[IO.File]::Create((Join-Path $logs 'minecraft-error.log'))
    try{
        $outCopy=$p.StandardOutput.BaseStream.CopyToAsync($outputFile);$errCopy=$p.StandardError.BaseStream.CopyToAsync($errorFile)
        Write-Host ('TENSURA_GAME_STARTED|'+$p.Id)
        $p.WaitForExit();$outCopy.GetAwaiter().GetResult();$errCopy.GetAwaiter().GetResult()
        if($p.ExitCode -ne 0){throw "Minecraft se cerró con código $($p.ExitCode). Revisá el registro del juego."}
        Write-Host 'TENSURA_GAME_CLOSED'
    }finally{$outputFile.Dispose();$errorFile.Dispose();$p.Dispose()}
}catch{Write-Host ('TENSURA_ERROR|'+$_.Exception.Message);exit 1}finally{if($sessionLock){$sessionLock.Dispose()}}
