"""Compile launcher 1.2 from reviewed sources; never reuse the retired executable."""
from pathlib import Path
import hashlib, json, os, subprocess, zipfile

BASE = Path(__file__).resolve().parent
PACK = BASE.parents[1]
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

for file in BASE.glob('*.ps1'):
    file.write_bytes(b'\xef\xbb\xbf' + file.read_text(encoding='utf-8-sig').replace('\r\n', '\n').encode('utf-8'))
(BASE / 'Launcher-Ui.cs').write_bytes((BASE / 'src/TensuraLauncher.cs').read_bytes())
compiler = Path(os.environ.get('WINDIR', r'C:\Windows')) / 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
executable = BASE / 'Tensura-Launcher.exe'
subprocess.run([str(compiler), '/nologo', '/target:winexe', '/platform:x64', '/optimize+',
                '/debug-', '/codepage:65001', '/utf8output',
                '/reference:System.Windows.Forms.dll', '/reference:System.Drawing.dll',
                '/reference:System.Web.Extensions.dll', '/reference:System.Core.dll',
                '/win32manifest:' + str(BASE / 'app.manifest'), '/out:' + str(executable),
                str(BASE / 'src/TensuraLauncher.cs')], check=True)
files = [(name, 'updater/launcher/' + name, BASE / name) for name in
         ['Launcher-Engine.ps1', 'AssetStore.cs', 'CpuBudget.ps1', 'TensuraLauncher.ps1', 'Launcher-Ui.cs']]
files.append(('Actualizar-Tensura-Cliente.ps1', 'updater/client/Bootstrap.ps1', PACK / 'updater/client/Bootstrap.ps1'))
release = json.loads((BASE / 'release.json').read_text(encoding='utf-8'))
release['version'] = '1.2.0'
release['files'] = [{'name': name, 'path': path, 'sha256': sha(file)} for name, path, file in files]
release['scriptLauncher'] = {'path': 'updater/launcher/TensuraLauncher.ps1', 'sha256': sha(BASE / 'TensuraLauncher.ps1')}
release.pop('application', None)
release['portableApplication'] = {'path': 'updater/launcher/Tensura-Launcher.exe', 'sha256': sha(executable)}
(BASE / 'release.json').write_bytes((json.dumps(release, indent=2, ensure_ascii=False) + '\n').encode('utf-8'))
with zipfile.ZipFile(BASE / 'Tensura-Launcher-Scripts.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
    for file, name in [(PACK / 'Tensura-Launcher.bat', 'Tensura-Launcher.bat'),
                       (PACK / 'Actualizar-Tensura-Cliente.ps1', 'Actualizar-Tensura-Cliente.ps1'),
                       (BASE / 'LEEME.txt', 'LEEME.txt')]:
        archive.write(file, name)
with zipfile.ZipFile(BASE / 'Tensura-Launcher-1.2.0.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
    archive.write(executable, 'Tensura-Launcher.exe')
    archive.write(BASE / 'LEEME.txt', 'LEEME.txt')
client_file = PACK / 'updater/client/release.json'
client = json.loads(client_file.read_text(encoding='utf-8'))
client.pop('standaloneLauncher', None)
client['scriptLauncher'] = release['scriptLauncher']
client['portableLauncher'] = release['portableApplication']
client_file.write_bytes((json.dumps(client, indent=2, ensure_ascii=False) + '\n').encode('utf-8'))
print('Tensura launcher 1.2.0 compiled from source; scan the new EXE before executing or publishing it')
