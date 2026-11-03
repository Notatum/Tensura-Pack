function Get-TensuraCpuPlan {
    param([int]$CpuCount=0,[int]$CpuPercent=80,[Nullable[UInt64]]$AvailableMask)
    if ($null -eq $AvailableMask) {
        $current=[Diagnostics.Process]::GetCurrentProcess()
        try { $bits=[BitConverter]::ToUInt64([BitConverter]::GetBytes($current.ProcessorAffinity.ToInt64()),0) } finally { $current.Dispose() }
    } else { $bits=[UInt64]$AvailableMask }
    $ids=@(for($index=0;$index -lt [IntPtr]::Size*8;$index++){if($bits -band ([UInt64]1 -shl $index)){$index}})
    if (!$ids.Count -or $CpuCount -lt 0 -or $CpuCount -gt $ids.Count -or $CpuPercent -lt 1 -or $CpuPercent -gt 100) { throw 'La cantidad de procesadores solicitada no está disponible.' }
    $count=if($CpuCount -eq 0){[Math]::Max(1,[int][Math]::Floor($ids.Count*$CpuPercent/100.0))}else{$CpuCount}
    [UInt64]$selected=0
    for($index=0;$index -lt $count;$index++){$selected=$selected -bor ([UInt64]1 -shl $ids[[int][Math]::Floor($index*$ids.Count/[double]$count)])}
    $signed=[BitConverter]::ToInt64([BitConverter]::GetBytes($selected),0)
    return [pscustomobject]@{count=$count;available=$ids.Count;mode=$(if($CpuCount -eq 0){'automatic'}else{'manual'});percent=$CpuPercent;affinity=[IntPtr]::new($signed);maskHex=$selected.ToString('x')}
}

function Start-TensuraCpuProcess {
    param([Parameter(Mandatory=$true)][Diagnostics.ProcessStartInfo]$StartInfo,[Parameter(Mandatory=$true)]$Plan)
    # Windows makes the new process inherit this mask. Restore only this worker
    # after creating Java; the already running GUI and other apps are unchanged.
    $worker=[Diagnostics.Process]::GetCurrentProcess();$original=$worker.ProcessorAffinity
    try {
        $worker.ProcessorAffinity=$Plan.affinity
        $child=[Diagnostics.Process]::Start($StartInfo)
        return $child
    } finally { $worker.ProcessorAffinity=$original;$worker.Dispose() }
}
