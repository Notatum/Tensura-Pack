"""Orb1.3.2: add30blocks only to the final form radius, retaining audited1.3.1."""
from pathlib import Path
import argparse,copy,hashlib,json,os,re,shutil,subprocess,zipfile
BASE=Path(__file__).resolve().parent
parser=argparse.ArgumentParser()
for name in ('server-root','original','work-dir','output'):parser.add_argument('--'+name,type=Path,required=True)
parser.add_argument('--java-home',type=Path);args=parser.parse_args()
root,work,output=args.server_root.resolve(),args.work_dir.resolve(),args.output.resolve()
assert work.is_relative_to(root/'maintenance') and output.is_relative_to(root/'maintenance'),'Private build outputs required'
sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
expected='9fff0064d34f2d590f27cbe5e7fd9d30ac31a7b1bff4e614b9044972f77dd979'
assert sha(args.original)==expected,'Original must be the audited Orb1.3.1 with audio and item protections'
work.mkdir(parents=True,exist_ok=True);output.parent.mkdir(parents=True,exist_ok=True)
classes=work/'classes';classes.mkdir(exist_ok=True)
java=str(args.java_home/'bin/java.exe') if args.java_home else shutil.which('java')
javac=str(args.java_home/'bin/javac.exe') if args.java_home else shutil.which('javac')
libraries=[root/f'libraries/org/ow2/asm/{a}/9.10.1/{a}-9.10.1.jar' for a in ('asm','asm-tree')]
subprocess.run([javac,'-J-Xmx128m','--release','21','-encoding','UTF-8','-cp',os.pathsep.join(map(str,libraries)),'-d',str(classes),str(BASE/'PatchOrbRange.java')],check=True)
name='ar/juan/devouringorb/OrbBalance.class';target=classes/name;target.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(args.original) as old:
    target.write_bytes(old.read(name))
    subprocess.run([java,'-Xmx64m','-cp',os.pathsep.join(map(str,[classes]+libraries)),'PatchOrbRange',str(target)],check=True)
    metadata,count=re.subn(r'(?m)^version\s*=\s*"1\.3\.1"','version="1.3.2"',old.read('META-INF/neoforge.mods.toml').decode('utf-8-sig'));assert count==1
    replacements={name:target.read_bytes(),'META-INF/neoforge.mods.toml':metadata.encode('utf-8')}
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as new:
        for item in old.infolist():new.writestr(copy.copy(item),replacements.get(item.filename,old.read(item)))
    with zipfile.ZipFile(output) as new:
        assert new.testzip() is None and old.namelist()==new.namelist()
        changed=[n for n in old.namelist() if old.read(n)!=new.read(n)]
        assert set(changed)==set(replacements),changed
report={'previousSha256':expected,'file':output.name,'sha256':sha(output),'bytes':output.stat().st_size,'changedEntries':changed,'oldRadii':[15,18,22,25,29,32,36,39,43,50],'newRadii':[15,18,22,25,29,32,36,39,43,80],'allOtherEntriesByteIdentical':True}
(work/'build-results.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))
