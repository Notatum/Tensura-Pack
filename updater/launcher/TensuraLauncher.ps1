param(
    [string]$DataRoot=(Join-Path $env:LOCALAPPDATA 'TensuraLauncher'),
    [string]$PlayerName=$env:TENSURA_PLAYER,
    [ValidateSet('','Update','Repair','Recipe')][string]$Action='',
    [string]$PackUrl='',
    [string]$ManifestUrl='',
    [string]$RenderUi='',
    [switch]$SelfTest
)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
$OutputEncoding=[Text.UTF8Encoding]::new($false)
if([Threading.Thread]::CurrentThread.ApartmentState -ne 'STA'){throw 'Abrí el launcher con PowerShell -STA.'}
Add-Type -AssemblyName System.Windows.Forms,System.Drawing,System.Web.Extensions
$source=Join-Path $PSScriptRoot 'Launcher-Ui.cs'
if(!(Test-Path -LiteralPath $source)){throw 'Falta la interfaz del launcher. Volvé a abrir el acceso para descargarla.'}
Add-Type -Path $source -ReferencedAssemblies System.Windows.Forms,System.Drawing,System.Web.Extensions,System.Core
$arguments=[Collections.Generic.List[string]]::new()
$arguments.Add('--data-dir');$arguments.Add([IO.Path]::GetFullPath($DataRoot))
if($PlayerName){$arguments.Add('--player');$arguments.Add($PlayerName)}
if($PackUrl){$arguments.Add('--pack-url');$arguments.Add($PackUrl)}
if($ManifestUrl){$arguments.Add('--manifest-url');$arguments.Add($ManifestUrl)}
if($RenderUi){$arguments.Add('--render-ui');$arguments.Add([IO.Path]::GetFullPath($RenderUi))}
if($SelfTest){$arguments.Add('--self-test')}
if($Action -eq 'Recipe'){$arguments.Add('--recipe')}
elseif($Action){$arguments.Add('--'+$Action.ToLowerInvariant())}
exit [TensuraLauncher.Program]::RunScript($arguments.ToArray())
