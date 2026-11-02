param(
    [string]$GameDir = (Join-Path $env:APPDATA '.tlauncher\legacy\Minecraft\game'),
    [string]$SourceMods = (Join-Path $PSScriptRoot 'mods')
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (!(Test-Path -LiteralPath $GameDir -PathType Container)) { throw "No existe la carpeta del juego: $GameDir. Usá -GameDir para indicar otra instalación." }
$gameRoot = (Resolve-Path -LiteralPath $GameDir).Path.TrimEnd('\')
if ($gameRoot -eq [IO.Path]::GetPathRoot($gameRoot).TrimEnd('\')) { throw 'La carpeta del juego no puede ser la raíz de un disco.' }
if (!(Test-Path -LiteralPath $SourceMods -PathType Container)) { throw "Falta la carpeta de mods verificados: $SourceMods" }
$sourceRoot = (Resolve-Path -LiteralPath $SourceMods).Path.TrimEnd('\')
$targetMods = Join-Path $gameRoot 'mods'
if ($sourceRoot -eq $targetMods) { throw 'La carpeta de origen y la de destino deben ser distintas.' }
$running = @(Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -and $_.CommandLine.IndexOf($gameRoot, [StringComparison]::OrdinalIgnoreCase) -ge 0 })
if ($running.Count) { throw 'Cerrá Minecraft antes de actualizar esta instalación.' }
$sources = @(Get-ChildItem -LiteralPath $sourceRoot -Filter '*.jar' -File)
if ($sources.Count -eq 0) { throw 'El paquete no contiene mods.' }
$manifestPath = Join-Path $sourceRoot 'mods.sha256'
if (!(Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw 'Falta mods.sha256; no se modificó la instalación.' }
$expected = @{}
foreach ($line in [IO.File]::ReadAllLines($manifestPath)) {
    if (!$line.Trim()) { continue }
    if ($line -notmatch '^([0-9a-f]{64})\t([^\\/]+\.jar)$') { throw 'El manifiesto de mods no es válido.' }
    if ($expected.ContainsKey($Matches[2])) { throw 'El manifiesto contiene nombres repetidos.' }
    $expected[$Matches[2]] = $Matches[1]
}
if ($expected.Count -ne $sources.Count) { throw 'La cantidad de mods no coincide con el manifiesto.' }
foreach ($source in $sources) {
    if (($source.Attributes -band [IO.FileAttributes]::ReparsePoint) -or !$expected.ContainsKey($source.Name) -or (Get-FileHash -LiteralPath $source.FullName -Algorithm SHA256).Hash -ne $expected[$source.Name]) {
        throw "El archivo $($source.Name) no coincide con el paquete verificado. No se modificó la instalación."
    }
}
function Get-ModIds([string]$JarPath) {
    $archive = [IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $entry = $archive.GetEntry('META-INF/neoforge.mods.toml')
        if (!$entry) { $entry = $archive.GetEntry('META-INF/mods.toml') }
        if (!$entry) { return }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $metadata = $reader.ReadToEnd() } finally { $reader.Dispose() }
        # Only IDs declared in [[mods]] identify this JAR. Dependency IDs do not.
        foreach ($block in [regex]::Matches($metadata, '(?ms)^\s*\[\[mods\]\][^\r\n]*\r?\n(.*?)(?=^\s*\[|\z)')) {
            $id = [regex]::Match($block.Groups[1].Value, '(?m)^\s*modId\s*=\s*["'']([^"'']+)["'']')
            if ($id.Success) { $id.Groups[1].Value }
        }
    } finally { $archive.Dispose() }
}
$sourceIds = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$sourceNames = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($source in $sources) {
    [void]$sourceNames.Add($source.Name)
    foreach ($id in (Get-ModIds $source.FullName)) { [void]$sourceIds.Add($id) }
}
$vehicleIds = '^(mts|mtsofficialpack|nihon_racing|oamp|svmp|vehiclesofsurvival|ccynho|panzerkrieg|iv_tpp.*|iv_tcp_v3.*|blockyrage.*)$'
$retire = @()
if (Test-Path -LiteralPath $targetMods) {
    $retire = @(foreach ($old in (Get-ChildItem -LiteralPath $targetMods -Filter '*.jar' -File)) {
        $replace = $sourceNames.Contains($old.Name)
        foreach ($id in (Get-ModIds $old.FullName)) { if ($sourceIds.Contains($id) -or $id -match $vehicleIds) { $replace = $true } }
        if ($replace) { $old }
    })
}
$backup = Join-Path $gameRoot ('.tensura-backups\' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$backupResolved = [IO.Path]::GetFullPath($backup)
if (!$backupResolved.StartsWith($gameRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'La copia de seguridad queda fuera de la instalación.' }
$moved = [Collections.Generic.List[object]]::new()
$installed = [Collections.Generic.List[string]]::new()
function Backup-File([string]$Path) {
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) { return }
    $relative = $Path.Substring($gameRoot.Length + 1)
    $saved = Join-Path $backupResolved $relative
    New-Item -ItemType Directory -Path (Split-Path -Parent $saved) -Force | Out-Null
    Move-Item -LiteralPath $Path -Destination $saved
    $moved.Add(@{ Original=$Path; Saved=$saved })
}
try {
    New-Item -ItemType Directory -Path $backupResolved -Force | Out-Null
    New-Item -ItemType Directory -Path $targetMods -Force | Out-Null
    foreach ($old in $retire) { Backup-File $old.FullName }
    foreach ($source in $sources) {
        $destination = Join-Path $targetMods $source.Name
        $installed.Add($destination)
        Copy-Item -LiteralPath $source.FullName -Destination $destination
    }
    $packs = Join-Path $PSScriptRoot 'resourcepacks'
    if (Test-Path -LiteralPath $packs) {
        $targetPacks = Join-Path $gameRoot 'resourcepacks'
        New-Item -ItemType Directory -Path $targetPacks -Force | Out-Null
        foreach ($pack in (Get-ChildItem -LiteralPath $packs -Filter '*.zip' -File)) {
            $destination = Join-Path $targetPacks $pack.Name
            Backup-File $destination
            $installed.Add($destination)
            Copy-Item -LiteralPath $pack.FullName -Destination $destination
        }
    }
    $options = Join-Path $gameRoot 'options.txt'
    $text = if (Test-Path -LiteralPath $options) { [IO.File]::ReadAllText($options) } else { '' }
    Backup-File $options
    if ($text -match '(?m)^lang:') { $text = [regex]::Replace($text, '(?m)^lang:[^\r\n]*', 'lang:es_ar') }
    else { $text = $text.TrimEnd() + "`nlang:es_ar`n" }
    $installed.Add($options)
    [IO.File]::WriteAllText($options, $text, [Text.UTF8Encoding]::new($false))
    & (Join-Path $PSScriptRoot 'Configurar-Tensura-Idioma.ps1') -GameDir $gameRoot
} catch {
    $failure = $_
    foreach ($path in $installed) { if (Test-Path -LiteralPath $path -PathType Leaf) { Remove-Item -LiteralPath $path -Force } }
    foreach ($entry in $moved) { Move-Item -LiteralPath $entry.Saved -Destination $entry.Original -Force }
    throw "Se canceló la actualización y se restauraron los archivos anteriores: $($failure.Exception.Message)"
}
Write-Host "Tensura actualizado en español. Se conservaron las partidas y los mods adicionales del cliente. Respaldo: $backupResolved"
