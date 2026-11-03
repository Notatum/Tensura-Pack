param([string]$WorkDir=(Join-Path $env:TEMP 'Tensura-Cpu-Test'))
$ErrorActionPreference='Stop'
$pack=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
New-Item -ItemType Directory -Path $WorkDir -Force | Out-Null
& javac -d $WorkDir (Join-Path $PSScriptRoot 'CpuProbe.java')
if($LASTEXITCODE -ne 0){throw 'Could not compile isolated Java probe'}
. (Join-Path $pack 'updater/launcher/CpuBudget.ps1')
$auto=Get-TensuraCpuPlan -AvailableMask 65535
if($auto.count -ne 12 -or $auto.available -ne 16){throw 'Default 80 percent failed'}
$one=Get-TensuraCpuPlan -AvailableMask 1
if($one.count -ne 1){throw 'Single processor failed'}
$manual=Get-TensuraCpuPlan -CpuCount 3 -AvailableMask 165
if($manual.count -ne 3 -or $manual.available -ne 4 -or $manual.maskHex -ne '25'){throw 'Sparse affinity failed'}
$invalid=$false;try{Get-TensuraCpuPlan -CpuCount 17 -AvailableMask 65535 | Out-Null}catch{$invalid=$true}
if(!$invalid){throw 'Invalid count accepted'}
$process=[Diagnostics.Process]::GetCurrentProcess();$before=$process.ProcessorAffinity
$plan=Get-TensuraCpuPlan -CpuCount 2
$start=[Diagnostics.ProcessStartInfo]::new();$start.FileName=(Get-Command java.exe).Source;$start.Arguments='-XX:ActiveProcessorCount=2 -cp "'+$WorkDir+'" CpuProbe';$start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardOutput=$true
$child=Start-TensuraCpuProcess -StartInfo $start -Plan $plan
try{
    $process.Refresh();if($process.ProcessorAffinity -ne $before){throw 'Parent affinity was not restored'}
    $child.Refresh();if($child.ProcessorAffinity -ne $plan.affinity){throw 'Child affinity was not inherited'}
    $text=$child.StandardOutput.ReadToEnd();$child.WaitForExit();if([int]$text.Trim() -ne 2){throw 'Child did not see two logical processors'}
}finally{$child.Dispose();$process.Dispose()}
[pscustomobject]@{defaultCount=$auto.count;available=$auto.available;sparseMask=$manual.maskHex;parentRestored=$true;childInherited=$true;childLogicalProcessors=2;invalidRejected=$true}|ConvertTo-Json
