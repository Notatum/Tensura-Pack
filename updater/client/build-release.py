"""Refresh hashes of the managed updater after editing its PowerShell sources."""
from pathlib import Path
import hashlib,json,shutil
BASE=Path(__file__).resolve().parent
PACK=BASE.parents[1]
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
manifest=json.loads((BASE/'release.json').read_text(encoding='utf-8'))
for p in list(BASE.glob('*.ps1'))+[PACK/'Configurar-Tensura-Idioma.ps1']:
    p.write_bytes(b'\xef\xbb\xbf'+p.read_text(encoding='utf-8-sig').replace('\r\n','\n').encode('utf-8'))
for record in manifest['files']:record['sha256']=sha(BASE/record['name'])
(BASE/'release.json').write_bytes((json.dumps(manifest,ensure_ascii=False,indent=2)+'\n').encode('utf-8'))
shutil.copy2(BASE/'Bootstrap.ps1',PACK/'Actualizar-Tensura-Cliente.ps1')
print('Managed updater hashes refreshed. Run packwiz refresh before publishing.')
