param(
    [Parameter(Mandatory=$true)][string]$Target,
    [Parameter(Mandatory=$true)][string]$DataRoot,
    [string]$Manifest,
    [string]$PackUrl,
    [string]$Source,
    [string]$Expected,
    [string]$PreviousHash,
    [int]$WaitPid=0,
    [long]$WaitStartedTicks=0,
    [switch]$Apply,
    [switch]$Restart
)
$ErrorActionPreference='Stop'
[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
$root=[IO.Path]::GetFullPath($DataRoot).TrimEnd('\')
$targetPath=[IO.Path]::GetFullPath($Target)
$log=Join-Path $root 'logs\launcher-update.log'
function Log([string]$Message) {
    New-Item -ItemType Directory -Path (Split-Path -Parent $log) -Force | Out-Null
    [IO.File]::AppendAllText($log,(Get-Date -Format s)+' '+$Message+"`r`n",[Text.UTF8Encoding]::new($false))
}
function No-Links([string]$Path) {
    $cursor=$Path
    while($cursor){
        if((Test-Path -LiteralPath $cursor) -and ((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'La actualización no admite enlaces de archivos o carpetas.'}
        $parent=Split-Path -Parent $cursor
        if($parent -eq $cursor){break};$cursor=$parent
    }
}
function Hash([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Version([string]$Path) {
    $info=[Diagnostics.FileVersionInfo]::GetVersionInfo($Path)
    if($info.ProductName -ne 'Tensura Launcher'){throw 'El archivo no pertenece a Tensura Launcher.'}
    $value=[version]$info.FileVersion
    return [version]::new($value.Major,$value.Minor,$value.Build)
}
function Arg([string]$Text) {
    # Arguments here are paths, hashes, numbers and switches. Quotes cannot
    # occur in a Windows path; reject them rather than constructing shell code.
    if($Text.Contains('"')){throw 'Argumento de actualización inválido.'}
    return '"'+$Text.TrimEnd('\')+'"'
}
$workerLock=$null;$temporary=$null
try {
    if($root -eq [IO.Path]::GetPathRoot($root).TrimEnd('\') -or [IO.Path]::GetExtension($targetPath) -ne '.exe'){throw 'Ruta de actualización inválida.'}
    No-Links $root;No-Links $targetPath
    if(!(Test-Path -LiteralPath $targetPath -PathType Leaf)){throw 'El ejecutable de destino ya no está disponible; no se restaurará.'}
    $currentVersion=Version $targetPath
    if(!$Apply){
        $release=Get-Content -LiteralPath $Manifest -Raw -Encoding UTF8 | ConvertFrom-Json
        if($release.schema -ne 1 -or $release.portableApplication.path -ne 'updater/launcher/Tensura-Launcher.exe' -or $release.portableApplication.sha256 -notmatch '^[a-f0-9]{64}$'){throw 'Publicación de launcher inválida.'}
        $publishedVersion=[version]$release.version
        if($publishedVersion -le $currentVersion){Write-Output 'TENSURA_LAUNCHER_CURRENT';return}
        if(([uri]$PackUrl).Scheme -notin @('http','https')){throw 'URL de actualización inválida.'}
        $Expected=[string]$release.portableApplication.sha256
        $PreviousHash=Hash $targetPath
        $directory=Join-Path $root ('cache\launcher-updates\'+$Expected)
        No-Links $directory
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
        $Source=Join-Path $directory 'Tensura-Launcher.exe'
        if(!(Test-Path -LiteralPath $Source) -or (Hash $Source) -ne $Expected){
            $download=$Source+'.download'
            Invoke-WebRequest -UseBasicParsing -Uri ([uri]::new([uri]$PackUrl,$release.portableApplication.path)) -OutFile $download -TimeoutSec 60
            if((Hash $download) -ne $Expected){Remove-Item -LiteralPath $download -Force;throw 'La nueva versión del launcher no coincide con su SHA-256.'}
            Move-Item -LiteralPath $download -Destination $Source -Force
        }
        if((Version $Source) -ne $publishedVersion){throw 'La versión del ejecutable no coincide con su publicación.'}
        if($WaitPid -gt 0){
            $parent=Get-Process -Id $WaitPid -ErrorAction SilentlyContinue
            if($parent){
                if([IO.Path]::GetFullPath($parent.Path) -ne $targetPath){throw 'El proceso de espera no corresponde al launcher de destino.'}
                $WaitStartedTicks=$parent.StartTime.ToUniversalTime().Ticks
            }
        }
        $arguments=@('-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File',$PSCommandPath,'-Apply','-Target',$targetPath,'-DataRoot',$root,'-Source',$Source,'-Expected',$Expected,'-PreviousHash',$PreviousHash,'-WaitPid',[string]$WaitPid,'-WaitStartedTicks',[string]$WaitStartedTicks)
        if($Restart){$arguments+='-Restart'}
        # ShellExecute detaches every inherited pipe handle. Redirecting only
        # stdout/stderr with Start-Process still leaked the caller's pipe handles
        # on Windows PowerShell 5.1, causing a circular wait with the UI.
        $workerInfo=[Diagnostics.ProcessStartInfo]::new('powershell.exe',(($arguments | ForEach-Object {Arg $_}) -join ' '))
        $workerInfo.UseShellExecute=$true;$workerInfo.WindowStyle=[Diagnostics.ProcessWindowStyle]::Hidden
        $workerProcess=[Diagnostics.Process]::Start($workerInfo);$workerProcess.Dispose()
        Log ('Actualización '+$publishedVersion+' preparada; se aplicará al cerrar este launcher.')
        Write-Output 'TENSURA_LAUNCHER_UPDATE_READY'
        return
    }
    if($Expected -notmatch '^[a-f0-9]{64}$' -or $PreviousHash -notmatch '^[a-f0-9]{64}$'){throw 'Hashes de actualización inválidos.'}
    $sourcePath=[IO.Path]::GetFullPath($Source)
    $expectedSource=Join-Path $root ('cache\launcher-updates\'+$Expected+'\Tensura-Launcher.exe')
    if($sourcePath -ne $expectedSource){throw 'La actualización debe proceder de la caché verificada.'}
    No-Links $sourcePath
    $workerLock=[IO.File]::Open((Join-Path $root 'cache\launcher-updates\update.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    if($WaitPid -gt 0){
        $parent=Get-Process -Id $WaitPid -ErrorAction SilentlyContinue
        if($parent){
            if($parent.StartTime.ToUniversalTime().Ticks -ne $WaitStartedTicks -or [IO.Path]::GetFullPath($parent.Path) -ne $targetPath){throw 'El proceso de espera cambió; se canceló la actualización.'}
            if(!$parent.WaitForExit(86400000)){throw 'El launcher sigue abierto; se pospone su actualización.'}
            $parent.Dispose()
        }
    }
    if(!(Test-Path -LiteralPath $targetPath) -or !(Test-Path -LiteralPath $sourcePath)){throw 'Falta un archivo de actualización; no se restaurarán archivos retirados por el sistema.'}
    if((Hash $targetPath) -eq $Expected){Log 'El launcher ya está actualizado.';return}
    if((Hash $targetPath) -ne $PreviousHash -or (Hash $sourcePath) -ne $Expected -or (Version $sourcePath) -le (Version $targetPath)){throw 'Los archivos de actualización cambiaron; operación cancelada.'}
    No-Links $targetPath
    $temporary=Join-Path (Split-Path -Parent $targetPath) ('.tensura-update-'+[guid]::NewGuid().ToString('N')+'.tmp')
    Copy-Item -LiteralPath $sourcePath -Destination $temporary
    if((Hash $temporary) -ne $Expected){throw 'La copia de actualización no coincide con el SHA-256.'}
    [IO.File]::Replace($temporary,$targetPath,($targetPath+'.previous'),$true)
    $temporary=$null
    if((Hash $targetPath) -ne $Expected){throw 'No se pudo verificar el launcher actualizado.'}
    Log 'Launcher actualizado en el mismo archivo; versión anterior respaldada en .previous.'
    if($Restart){
        $info=[Diagnostics.ProcessStartInfo]::new($targetPath,('--data-dir '+(Arg $root)));$info.UseShellExecute=$false;$info.CreateNoWindow=$true
        [void][Diagnostics.Process]::Start($info)
    }
} catch {
    Log ('ERROR: '+$_.Exception.Message)
    Write-Error ('Actualización del launcher: '+$_.Exception.Message)
    exit 1
} finally {
    if($workerLock){$workerLock.Dispose()}
    if($temporary -and (Test-Path -LiteralPath $temporary)){Remove-Item -LiteralPath $temporary -Force}
}
