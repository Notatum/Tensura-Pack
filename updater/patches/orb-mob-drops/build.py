"""Fix mob-source ownership in exact Orb1.3.5; retain every other game feature."""
from pathlib import Path
import argparse,copy,hashlib,json,os,re,subprocess,zipfile
BASE=Path(__file__).resolve().parent
parser=argparse.ArgumentParser()
for key in ('server-root','original','work-dir','output','java-home'):parser.add_argument('--'+key,type=Path,required=True)
args=parser.parse_args();root=args.server_root.resolve();work=args.work_dir.resolve();output=args.output.resolve()
assert work.is_relative_to(root/'maintenance') and output.is_relative_to(root/'maintenance')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(args.original)=='643ce41532dbfc48eb60000ec480be35d424e2309b9fe59fa5ee6975280f06f7'
work.mkdir(parents=True,exist_ok=True);output.parent.mkdir(parents=True,exist_ok=True);classes=work/'classes';classes.mkdir(exist_ok=True)
mc=root/'libraries/net/minecraft/server/1.21.1-20240808.144430/server-1.21.1-20240808.144430-srg.jar'
jars=[mc,args.original]+sorted((root/'libraries').rglob('*.jar'))+sorted((root/'mods').glob('*.jar'))+sorted((root/'maintenance/upstream').glob('manascore-*.jar'))
values=['-proc:none','--release','21','-g:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes)]+[str(p) for p in sorted((BASE/'src').rglob('*.java'))]
argfile=work/'javac.args';argfile.write_text('\n'.join('"'+v.replace('\\','/')+'"' for v in values),encoding='utf-8')
subprocess.run([str(args.java_home/'bin/javac.exe'),'-J-Xmx128m','@'+str(argfile)],check=True)
with zipfile.ZipFile(args.original) as old:
    meta,n=re.subn(r'(?m)^version\s*=\s*"1\.3\.5"','version="1.3.6"',old.read('META-INF/neoforge.mods.toml').decode('utf-8-sig'));assert n==1
    config=json.loads(old.read('devouring_orb.safety.mixins.json'));assert 'ItemSourceAccessor' not in config['mixins'];config['mixins'].extend(['ItemSourceAccessor','ItemDropSourceMixin'])
    changed={'META-INF/neoforge.mods.toml':meta.encode(),'devouring_orb.safety.mixins.json':(json.dumps(config,indent=2)+'\n').encode()}
    generated={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
    expected={'ar/juan/devouringorb/OrbFoodGuard.class'}
    assert set(generated)&set(old.namelist())==expected
    changed.update({n:b for n,b in generated.items() if n in expected});additions={n:b for n,b in generated.items() if n not in expected}
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as new:
        for entry in old.infolist():new.writestr(copy.copy(entry),changed.get(entry.filename,old.read(entry)))
        for name,body in sorted(additions.items()):
            entry=zipfile.ZipInfo(name,(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o644<<16;new.writestr(entry,body)
    with zipfile.ZipFile(output) as new:
        assert new.testzip() is None
        actual=[n for n in old.namelist() if old.read(n)!=new.read(n)];assert set(actual)==set(changed)
result={'file':output.name,'sha256':sha(output),'bytes':output.stat().st_size,'changedEntries':actual,'newEntries':sorted(additions),'allOtherEntriesByteIdentical':True,'pickupQueueAndDamageEntriesUnchanged':True,'nightmareJarModified':False}
(work/'build-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,indent=2))
