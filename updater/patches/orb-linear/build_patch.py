"""Build Orb 1.3.0 from the pinned, safety-fixed 1.2.1 without changing saved data."""
from pathlib import Path
import argparse,hashlib,json,os,re,subprocess,urllib.request,zipfile

BASE=Path(__file__).resolve().parent
PACK=BASE.parents[2]
parser=argparse.ArgumentParser()
parser.add_argument('--server-root',type=Path,default=PACK.parent)
parser.add_argument('--original',type=Path)
parser.add_argument('--output-dir',type=Path)
args=parser.parse_args();ROOT=args.server_root.resolve()
WORK=args.output_dir or ROOT/'maintenance/orb-linear-build';WORK.mkdir(parents=True,exist_ok=True)
old='Orbe-de-Beelzebuth-neoforge-1.21.1-1.2.1.jar'
original=args.original or ROOT/'mods'/old
if not original.exists():
    original=WORK/old
    urllib.request.urlretrieve('https://raw.githubusercontent.com/Notatum/Tensura-Pack/121a961fb9545e0430599b95683e90d90ddb46d0/mods/'+old,original)
expected=json.loads((PACK/'updater/patches/custom-item-safety/build-manifest.json').read_text())
expected=next(r['sha256'] for r in expected if r['file']==old)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
if sha(original)!=expected:raise ValueError('Orb 1.2.1 does not match the verified safety build')
classes=WORK/'classes';classes.mkdir(exist_ok=True)
cp=os.pathsep.join(map(str,[original]+list((ROOT/'libraries').rglob('*.jar'))+list((ROOT/'mods').glob('*.jar'))))
sources=sorted((BASE/'src').rglob('*.java'))+[BASE/'PatchOrbDamage.java']
arguments=['-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(classes)]+list(map(str,sources))
argfile=WORK/'javac.args';argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in arguments),encoding='utf-8')
subprocess.run(['javac','@'+str(argfile)],check=True)
patchcp=os.pathsep.join(map(str,[classes,ROOT/'libraries/org/ow2/asm/asm/9.10.1/asm-9.10.1.jar',ROOT/'libraries/org/ow2/asm/asm-tree/9.10.1/asm-tree-9.10.1.jar']))
replacement={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class') if p.name!='PatchOrbDamage.class'}
translations=json.loads((PACK/'updater/translations/devouring_orb/es_es.json').read_text(encoding='utf-8'))
output=WORK/'Orbe-de-Beelzebuth-neoforge-1.21.1-1.3.0.jar'
with zipfile.ZipFile(original) as source,zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as target:
    for name in ['ar/juan/devouringorb/OrbCombat.class','ar/juan/devouringorb/DevouringOrbItem.class','ar/juan/devouringorb/client/OrbSpeakerScreen.class']:
        file=classes/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(source.read(name))
        subprocess.run(['java','-cp',patchcp,'PatchOrbDamage',str(file)],check=True);replacement[name]=file.read_bytes()
    for entry in source.infolist():
        data=replacement.pop(entry.filename,source.read(entry))
        if entry.filename=='META-INF/neoforge.mods.toml':
            data=re.sub(r'(?m)^version\s*=\s*"1\.2\.1"','version="1.3.0"',data.decode('utf-8-sig'),count=1).encode()
        if entry.filename.startswith('assets/devouring_orb/lang/es_'):
            values=json.loads(data.decode('utf-8-sig'));values.update(translations);data=(json.dumps(values,ensure_ascii=False,indent=2)+'\n').encode()
        target.writestr(entry,data)
    for name,data in sorted(replacement.items()):
        entry=zipfile.ZipInfo(name,(2026,11,2,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;target.writestr(entry,data)
(WORK/'build-manifest.json').write_text(json.dumps({'previous':old,'baseSha256':expected,'file':output.name,'sha256':sha(output),'baseDamage':5,'damagePerEvolution':5,'epDamageRate':'0.00001'},indent=2)+'\n')
print('Built',output.name,sha(output))
