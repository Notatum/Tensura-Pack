"""Exercise retirement policy without opening games, migrating worlds or rebuilding artifacts."""
from pathlib import Path
import ast,copy,subprocess,tempfile

PACK=Path(__file__).resolve().parents[2]
worker=PACK/'updater/client/Aplicar-Cliente.ps1'
powershell=r'''
param([string]$Worker)
$errors=$null; $tokens=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($Worker,[ref]$tokens,[ref]$errors)
if($errors.Count){throw ($errors | Out-String)}
$definition=$ast.Find({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Should-RetireNightmare'},$true)
if(!$definition){throw 'Retirement guard is missing'}
Invoke-Expression $definition.Extent.Text
$requested=[pscustomobject]@{retiredContentMigration=[pscustomobject]@{revision='test'}}
$disabled=[pscustomobject]@{}
$cases=@(
    @{Label='Explicit retirement without Nightmare remains available'; Release=$requested; Mods=@{'tensura.jar'='verified'}; Expected=$true},
    @{Label='Restored Nightmare prevents retirement even if an old policy remains'; Release=$requested; Mods=@{'trnightmare-1.0.6.1-neoforge-1.21.1.jar'='verified'}; Expected=$false},
    @{Label='Filename guard is case-insensitive'; Release=$requested; Mods=@{'TRNIGHTMARE-1.0.6.1.jar'='verified'}; Expected=$false},
    @{Label='Removing release policy disables migration without Nightmare'; Release=$disabled; Mods=@{'tensura.jar'='verified'}; Expected=$false},
    @{Label='Restored mod with disabled policy preserves world data'; Release=$disabled; Mods=@{'trnightmare-1.0.6.1.jar'='verified'}; Expected=$false}
)
foreach($case in $cases){
    $actual=Should-RetireNightmare $case.Release $case.Mods
    if($actual -ne $case.Expected){throw $case.Label}
    Write-Output ('PASS: '+$case.Label)
}
'''
with tempfile.TemporaryDirectory(prefix='tensura-retirement-guard-') as temporary:
    test=Path(temporary)/'Test-Guard.ps1';test.write_bytes(b'\xef\xbb\xbf'+powershell.encode('utf-8'))
    subprocess.run(['powershell.exe','-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File',str(test),'-Worker',str(worker)],check=True)

source=PACK/'updater/shared/build_tools.py'
tree=ast.parse(source.read_text(encoding='utf-8'))
function=next(node for node in tree.body if isinstance(node,ast.FunctionDef) and node.name=='configure_retirement')
namespace={};exec(compile(ast.Module(body=[function],type_ignores=[]),str(source),'exec'),namespace)
configure=namespace['configure_retirement']
original={'schema':1,'vanillaGreatSage':{'revision':'official'},'retiredContentMigration':{'revision':'old'}}
restored=copy.deepcopy(original);configure(restored,True,'test-sha')
assert 'retiredContentMigration' not in restored
assert restored['vanillaGreatSage']==original['vanillaGreatSage'] and restored['schema']==1
print('PASS: rebuilding a restored Nightmare release removes the retirement policy and preserves other policies')
configure(restored,True,'another-sha');assert 'retiredContentMigration' not in restored
print('PASS: repeating the build cannot re-enable retirement while Nightmare is present')
retired=copy.deepcopy(restored);configure(retired,False,'verified-sha')
assert retired['retiredContentMigration']['sha256']=='verified-sha' and retired['retiredContentMigration']['revision']=='nightmare-retired-v2'
print('PASS: an intentional retirement release retains its verified migration policy')
print('Nightmare retirement guard: 8 checks, 0 errors')
