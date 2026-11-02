param([string]$Target,[string]$Source,[string]$Expected,[int]$WaitPid,[string]$DataRoot,[switch]$NoRestart)
$ErrorActionPreference='Stop'
$targetPath=[IO.Path]::GetFullPath($Target)
if((Split-Path -Leaf $targetPath) -ne 'TensuraLauncher.exe'){throw 'Destino de actualización inválido.'}
if($Expected -notmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath $Source -Algorithm SHA256).Hash -ne $Expected){throw 'El launcher descargado no es válido.'}
$process=Get-Process -Id $WaitPid -ErrorAction SilentlyContinue
if($process){$process.WaitForExit(60000)|Out-Null}
for($attempt=0;$attempt -lt 20;$attempt++){
    try{Copy-Item -LiteralPath $targetPath -Destination ($targetPath+'.previous') -Force;Copy-Item -LiteralPath $Source -Destination $targetPath -Force;break}
    catch{if($attempt -eq 19){throw};Start-Sleep -Milliseconds 300}
}
if(!$NoRestart){
    $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=$targetPath;$info.Arguments='--data-dir "'+$DataRoot.TrimEnd('\')+'"';$info.UseShellExecute=$false;$info.CreateNoWindow=$true
    [void][Diagnostics.Process]::Start($info)
}
