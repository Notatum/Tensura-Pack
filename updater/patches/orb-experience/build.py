"""Add XP pickup to audited Orb 1.3.2 while retaining every unrelated entry."""
from pathlib import Path
import argparse,copy,hashlib,json,os,re,shutil,subprocess,zipfile
BASE=Path(__file__).resolve().parent
parser=argparse.ArgumentParser()
for key in ('server-root','original','work-dir','output'):parser.add_argument('--'+key,type=Path,required=True)
parser.add_argument('--java-home',type=Path);args=parser.parse_args()
root,work,output=args.server_root.resolve(),args.work_dir.resolve(),args.output.resolve()
assert work.is_relative_to(root/'maintenance') and output.is_relative_to(root/'maintenance'),'Private build outputs required'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
expected='402c7b52e8d5cbad24a9e84f9f567a86adc15d9cbf710c603ba24ea44a3ac9c0'
assert sha(args.original)==expected,'Expected audited Orb1.3.2 with final radius80, audio4 and item protections'
work.mkdir(parents=True,exist_ok=True);output.parent.mkdir(parents=True,exist_ok=True)
classes=work/'classes';classes.mkdir(exist_ok=True)
java=str(args.java_home/'bin/java.exe') if args.java_home else shutil.which('java')
javac=str(args.java_home/'bin/javac.exe') if args.java_home else shutil.which('javac')
mc=root/'libraries/net/minecraft/server/1.21.1-20240808.144430/server-1.21.1-20240808.144430-srg.jar'
jars=[mc,args.original]+sorted((root/'libraries').rglob('*.jar'))
arguments=['-proc:none','--release','21','-g:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes),str(BASE/'PatchOrbExperience.java')]+[str(p) for p in sorted((BASE/'src').rglob('*.java'))]
argfile=work/'javac.args';argfile.write_text('\n'.join('"'+v.replace('\\','/')+'"' for v in arguments),encoding='utf-8')
subprocess.run([javac,'-J-Xmx128m','@'+str(argfile)],check=True)
asm=[root/f'libraries/org/ow2/asm/{a}/9.10.1/{a}-9.10.1.jar' for a in ('asm','asm-tree')]
name='ar/juan/devouringorb/OrbCombat.class';target=classes/name;target.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(args.original) as old:
    target.write_bytes(old.read(name))
    subprocess.run([java,'-Xmx64m','-cp',os.pathsep.join(map(str,[classes]+asm)),'PatchOrbExperience',str(target)],check=True)
    meta,count=re.subn(r'(?m)^version\s*=\s*"1\.3\.2"','version="1.3.3"',old.read('META-INF/neoforge.mods.toml').decode('utf-8-sig'));assert count==1
    additions={'ar/juan/devouringorb/OrbExperience.class':(classes/'ar/juan/devouringorb/OrbExperience.class').read_bytes()}
    assert not set(additions)&set(old.namelist())
    changed={name:target.read_bytes(),'META-INF/neoforge.mods.toml':meta.encode('utf-8')}
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as new:
        for item in old.infolist():new.writestr(copy.copy(item),changed.get(item.filename,old.read(item)))
        for filename,body in additions.items():
            info=zipfile.ZipInfo(filename,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o644<<16
            new.writestr(info,body)
    with zipfile.ZipFile(output) as new:
        assert new.testzip() is None and set(new.namelist())==set(old.namelist())|set(additions)
        actual=[n for n in old.namelist() if old.read(n)!=new.read(n)];assert set(actual)==set(changed),actual
report={'previousSha256':expected,'file':output.name,'sha256':sha(output),'bytes':output.stat().st_size,'changedEntries':actual,'newEntries':list(additions),'allOtherEntriesByteIdentical':True,'xpPath':'ExperienceOrb.playerTouch; preserves Clumps, Mending, pickup events','damageEPRangeAndAudioUnchanged':True}
(work/'build-results.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))
