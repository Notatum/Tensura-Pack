"""Build deterministic Java 21 migration/updater artifacts and refresh their publication hash."""
from pathlib import Path
import argparse,hashlib,json,subprocess,zipfile

def configure_retirement(manifest, nightmare_present, helper_sha256):
    """Never publish a destructive retirement step alongside the restored mod."""
    if nightmare_present:
        manifest.pop('retiredContentMigration',None)
    else:
        manifest['retiredContentMigration']={'revision':'nightmare-retired-v2','path':'updater/shared/RetiredContentMigration.jar','sha256':helper_sha256}

BASE=Path(__file__).resolve().parent;PACK=BASE.parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--server-root',type=Path,default=PACK.parent);args=parser.parse_args()
work=args.server_root/'maintenance/retired-content-build';classes=work/'classes';classes.mkdir(parents=True,exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','-d',str(classes),str(BASE/'RetiredContentMigration.java'),str(PACK/'updater/server/TensuraServerUpdater.java')],check=True)
for file,main in [(BASE/'RetiredContentMigration.jar','RetiredContentMigration'),(PACK/'updater/server/Actualizador-Tensura-Servidor.jar','TensuraServerUpdater')]:
    entries={'META-INF/MANIFEST.MF':('Manifest-Version: 1.0\r\nMain-Class: '+main+'\r\n\r\n').encode('ascii')}
    for p in classes.glob('*.class'):
        if main=='TensuraServerUpdater' or p.name.startswith('RetiredContentMigration'):entries[p.name]=p.read_bytes()
    with zipfile.ZipFile(file,'w',zipfile.ZIP_DEFLATED) as jar:
        for name,data in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,11,2,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;jar.writestr(info,data)
    print(file.name,hashlib.sha256(file.read_bytes()).hexdigest())
release=PACK/'updater/client/release.json';manifest=json.loads(release.read_text(encoding='utf-8'))
nightmare_present=any(p.name.lower().startswith('trnightmare-') for p in (PACK/'mods').glob('*.jar'))
configure_retirement(manifest,nightmare_present,hashlib.sha256((BASE/'RetiredContentMigration.jar').read_bytes()).hexdigest())
release.write_bytes((json.dumps(manifest,ensure_ascii=False,indent=2)+'\n').encode())
