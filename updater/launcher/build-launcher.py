"""Build the Windows launcher with the compiler included in .NET Framework 4.8."""
from pathlib import Path
import hashlib, json, os, subprocess, zipfile

BASE = Path(__file__).resolve().parent
PACK = BASE.parents[1]
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

for file in BASE.glob('*.ps1'):
    file.write_bytes(b'\xef\xbb\xbf' + file.read_text(encoding='utf-8-sig').replace('\r\n', '\n').encode('utf-8'))
csc = Path(os.environ.get('WINDIR', r'C:\Windows')) / 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
subprocess.run([str(csc), '/nologo', '/target:winexe', '/platform:x64', '/optimize+', '/utf8output', '/codepage:65001',
    '/win32manifest:' + str(BASE / 'app.manifest'), '/reference:System.Windows.Forms.dll',
    '/reference:System.Drawing.dll', '/reference:System.Web.Extensions.dll',
    '/out:' + str(BASE / 'TensuraLauncher.exe'), str(BASE / 'src/TensuraLauncher.cs')], check=True)
files = [(name, 'updater/launcher/' + name, BASE / name) for name in
         ['Launcher-Engine.ps1', 'AssetStore.cs', 'Apply-Launcher-Update.ps1']]
files.append(('Actualizar-Tensura-Cliente.ps1', 'updater/client/Bootstrap.ps1', PACK / 'updater/client/Bootstrap.ps1'))
release = {
    'schema': 1, 'version': '1.0.0',
    'application': {'path': 'updater/launcher/TensuraLauncher.exe', 'sha256': sha(BASE / 'TensuraLauncher.exe')},
    'files': [{'name': name, 'path': path, 'sha256': sha(file)} for name, path, file in files],
    'runtime': {
        'version': '21.0.13+9', 'vendor': 'Eclipse Temurin',
        'url': 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jre_x64_windows_hotspot_21.0.13_9.zip',
        'sha256': '2332c0b4cff6bed722da3567c91d5e47568bcba863fa4945d0a8d6fc76158628'
    }
}
(BASE / 'release.json').write_bytes((json.dumps(release, indent=2, ensure_ascii=False) + '\n').encode('utf-8'))
with zipfile.ZipFile(BASE / 'TensuraLauncher-Portable.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
    for name in ['TensuraLauncher.exe', 'LEEME.txt']:
        archive.write(BASE / name, name)
client_file = PACK / 'updater/client/release.json'
client = json.loads(client_file.read_text(encoding='utf-8'))
client['standaloneLauncher'] = release['application']
client_file.write_bytes((json.dumps(client, indent=2, ensure_ascii=False) + '\n').encode('utf-8'))
print('Tensura Launcher 1.0.0 built; EXE SHA256:', release['application']['sha256'])
