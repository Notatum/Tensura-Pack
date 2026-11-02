param([string]$GameDir,[string]$CacheDir,[string]$Java,[string]$Template,[string]$ReleaseManifest)
$ErrorActionPreference='Stop'
$javaCandidates=@($Java,(Join-Path $env:APPDATA '.tlauncher\legacy\Minecraft\jre\x64\bin\java.exe')) | Select-Object -Unique
$runtimeJava=$null
foreach($candidate in $javaCandidates){
    if(!$candidate -or !(Test-Path -LiteralPath $candidate)){continue}
    $check=[Diagnostics.ProcessStartInfo]::new();$check.FileName=$candidate;$check.Arguments='-version';$check.UseShellExecute=$false;$check.CreateNoWindow=$true;$check.RedirectStandardError=$true;$check.RedirectStandardOutput=$true
    $process=[Diagnostics.Process]::Start($check)
    try{$versionText=$process.StandardError.ReadToEnd()+$process.StandardOutput.ReadToEnd();$process.WaitForExit();if($versionText -match 'version "21[.\"]'){$runtimeJava=$candidate;break}}finally{$process.Dispose()}
}
if(!$runtimeJava){throw 'La instalación administrada requiere Java 21.'}
function Install-Runtime([string]$Installer,[string]$Directory){
    $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=$runtimeJava;$info.Arguments='-Djava.awt.headless=true -jar "'+$Installer+'" --installClient "'+$Directory+'"';$info.WorkingDirectory=$CacheDir;$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    $process=[Diagnostics.Process]::Start($info)
    try{
        $output=$process.StandardOutput.ReadToEndAsync();$errorOutput=$process.StandardError.ReadToEndAsync()
        if(!$process.WaitForExit(420000)){$process.Kill();throw 'La instalación de NeoForge excedió el tiempo de espera.'}
        Write-Host ($output.GetAwaiter().GetResult()+$errorOutput.GetAwaiter().GetResult())
        if($process.ExitCode -ne 0){throw "El instalador de NeoForge terminó con código $($process.ExitCode)."}
    }finally{$process.Dispose()}
}
$root=(Resolve-Path -LiteralPath $GameDir).Path.TrimEnd('\')
$policy=Get-Content -LiteralPath (Join-Path $root 'config\tensura-managed-client.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$release=Get-Content -LiteralPath $ReleaseManifest -Raw -Encoding UTF8 | ConvertFrom-Json
if ($policy.schema -ne 1 -or $policy.minecraft -ne $release.minecraft -or $policy.neoforge -ne $release.neoforge) { throw 'La política y el motor no corresponden a la misma plataforma.' }
if ($policy.minecraft -notmatch '^\d+(\.\d+){1,3}$' -or $policy.neoforge -notmatch '^\d+(\.\d+){1,3}$') { throw 'La versión de plataforma no es válida.' }
if ($policy.shaderPack -notmatch '^[^\\/]+\.zip$' -or $policy.profile -notmatch '^[A-Za-z0-9._-]+$') { throw 'La política contiene un nombre de archivo inválido.' }
foreach($folder in @('versions','libraries')){$null=Assert-Child (Join-Path $root $folder) $root}
$shader=Join-Path $root ('shaderpacks\'+$policy.shaderPack)
if (!(Test-Path -LiteralPath $shader) -or (Get-FileHash -LiteralPath $shader -Algorithm SHA256).Hash -ne $policy.shaderSha256) { throw 'El shader administrado no coincide con la publicación.' }
$baseVersion=Join-Path $root ('versions\'+$policy.minecraft+'\'+$policy.minecraft+'.jar')
$forgeRoot=Join-Path $root ('libraries\net\neoforged\neoforge\'+$policy.neoforge)
$ready=(Test-Path -LiteralPath $baseVersion) -and (Test-Path -LiteralPath (Join-Path $forgeRoot ('neoforge-'+$policy.neoforge+'-client.jar'))) -and (Test-Path -LiteralPath (Join-Path $forgeRoot ('neoforge-'+$policy.neoforge+'-universal.jar')))
if (!$ready) {
    $profileFile=Join-Path $root 'launcher_profiles.json'
    if (!(Test-Path -LiteralPath $profileFile)) { [IO.File]::WriteAllText($profileFile,'{"profiles":{}}',[Text.UTF8Encoding]::new($false)) }
    $installer=Join-Path $CacheDir ('neoforge-'+$policy.neoforge+'-installer.jar')
    if (!(Test-Path -LiteralPath $installer) -or (Get-FileHash -LiteralPath $installer -Algorithm SHA256).Hash -ne $release.installer.sha256) {
        Invoke-WebRequest -UseBasicParsing -Uri $release.installer.url -OutFile ($installer+'.download')
        if ((Get-FileHash -LiteralPath ($installer+'.download') -Algorithm SHA256).Hash -ne $release.installer.sha256) { throw 'El instalador de NeoForge está alterado.' }
        Move-Item -LiteralPath ($installer+'.download') -Destination $installer -Force
    }
    Write-Host "[Tensura] Instalando Minecraft $($policy.minecraft) y NeoForge $($policy.neoforge)."
    Install-Runtime $installer $root
}
$data=Get-Content -LiteralPath $Template -Raw -Encoding UTF8 | ConvertFrom-Json
if ($data.jar -ne $policy.minecraft -or $data.id -ne $policy.profile) { throw 'El perfil publicado no coincide con la política.' }
foreach ($dependency in $data.libraries) {
    if ($dependency.name -like ('net.neoforged:neoforge:'+$policy.neoforge+':*')) {
        $suffix=$dependency.name.Split(':')[-1]
        $file=Join-Path $forgeRoot ('neoforge-'+$policy.neoforge+'-'+$suffix+'.jar')
        if (!(Test-Path -LiteralPath $file)) { throw "Falta una biblioteca de NeoForge: $suffix" }
        $dependency.downloads.artifact.sha1=(Get-FileHash -LiteralPath $file -Algorithm SHA1).Hash.ToLowerInvariant()
        $dependency.downloads.artifact.size=(Get-Item -LiteralPath $file).Length
    }
}
$profileRoot=Join-Path $root ('versions\'+$policy.profile)
if (!(Test-Path -LiteralPath $profileRoot)) { New-Item -ItemType Directory -Path $profileRoot -Force | Out-Null }
$null=Assert-Child $profileRoot $root
[IO.File]::WriteAllText((Join-Path $profileRoot ($policy.profile+'.json')),($data | ConvertTo-Json -Depth 60),[Text.UTF8Encoding]::new($false))
$profileJar=Join-Path $profileRoot ($policy.profile+'.jar')
if (!(Test-Path -LiteralPath $profileJar) -or (Get-FileHash -LiteralPath $profileJar).Hash -ne (Get-FileHash -LiteralPath $baseVersion).Hash) { Copy-Item -LiteralPath $baseVersion -Destination $profileJar -Force }
Write-Host "[Tensura] Perfil administrado: $($policy.profile). Unbound obligatorio: $($policy.mandatoryShaders)."
return $policy
