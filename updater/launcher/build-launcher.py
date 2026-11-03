"""Build the verified script launcher without restoring or regenerating an EXE."""
from pathlib import Path
import hashlib, json, zipfile

BASE = Path(__file__).resolve().parent
PACK = BASE.parents[1]
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

for file in BASE.glob('*.ps1'):
    file.write_bytes(b'\xef\xbb\xbf' + file.read_text(encoding='utf-8-sig').replace('\r\n', '\n').encode('utf-8'))
(BASE / 'Launcher-Ui.cs').write_bytes((BASE / 'src/TensuraLauncher.cs').read_bytes())
files = [(name, 'updater/launcher/' + name, BASE / name) for name in
         ['Launcher-Engine.ps1', 'AssetStore.cs', 'Apply-Launcher-Update.ps1', 'CpuBudget.ps1', 'TensuraLauncher.ps1', 'Launcher-Ui.cs']]
files.append(('Actualizar-Tensura-Cliente.ps1', 'updater/client/Bootstrap.ps1', PACK / 'updater/client/Bootstrap.ps1'))
release = json.loads((BASE / 'release.json').read_text(encoding='utf-8'))
release['version'] = '1.1.0'
release['files'] = [{'name': name, 'path': path, 'sha256': sha(file)} for name, path, file in files]
release['scriptLauncher'] = {'path': 'updater/launcher/TensuraLauncher.ps1', 'sha256': sha(BASE / 'TensuraLauncher.ps1')}
# Keep the historical application metadata for existing launchers. Never read,
# rebuild, restore, bundle or execute the quarantined EXE in this workflow.
(BASE / 'release.json').write_bytes((json.dumps(release, indent=2, ensure_ascii=False) + '\n').encode('utf-8'))
with zipfile.ZipFile(BASE / 'Tensura-Launcher-Scripts.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
    for file, name in [(PACK / 'Tensura-Launcher.bat', 'Tensura-Launcher.bat'),
                       (PACK / 'Actualizar-Tensura-Cliente.ps1', 'Actualizar-Tensura-Cliente.ps1'),
                       (BASE / 'LEEME.txt', 'LEEME.txt')]:
        archive.write(file, name)
client_file = PACK / 'updater/client/release.json'
client = json.loads(client_file.read_text(encoding='utf-8'))
client.pop('standaloneLauncher', None)
client['scriptLauncher'] = release['scriptLauncher']
client_file.write_bytes((json.dumps(client, indent=2, ensure_ascii=False) + '\n').encode('utf-8'))
print('Tensura script launcher 1.1.0 built; no EXE read, generated or bundled')
