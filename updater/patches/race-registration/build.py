"""Build the standalone race registrar compatibility mod; private build output only."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,zipfile

BASE=Path(__file__).resolve().parent
p=argparse.ArgumentParser()
p.add_argument('--server-root',type=Path,required=True)
p.add_argument('--work-dir',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
p.add_argument('--java-home',type=Path,required=True)
args=p.parse_args()
root,work,output=args.server_root.resolve(),args.work_dir.resolve(),args.output.resolve()
assert work.is_relative_to(root/'maintenance') and output.is_relative_to(root/'maintenance')
work.mkdir(parents=True,exist_ok=True);output.parent.mkdir(parents=True,exist_ok=True)
classes=work/'classes'
if classes.exists():
    assert classes.resolve().is_relative_to(work)
    shutil.rmtree(classes)
classes.mkdir()
architectury=root/'mods/architectury-13.0.11-neoforge.jar'
assert hashlib.sha256(architectury.read_bytes()).hexdigest()=='9cc92f2c09533fc5482c60f993bd891c655a0e32b637037cdcb7c2b23adedeeb'
with zipfile.ZipFile(root/'libraries/net/neoforged/neoforge/21.1.250/neoforge-21.1.250-universal.jar') as jar:
    extras=work/'mixinextras-neoforge-0.5.3.jar'
    extras.write_bytes(jar.read('META-INF/jarjar/mixinextras-neoforge-0.5.3.jar'))
mc=root/'libraries/net/minecraft/server/1.21.1-20240808.144430/server-1.21.1-20240808.144430-srg.jar'
jars=[mc,architectury,extras,*sorted((root/'libraries').rglob('*.jar'))]
values=['-proc:none','--release','21','-g:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes)]
values.extend(str(f) for f in sorted((BASE/'src').rglob('*.java')))
argfile=work/'javac.args';argfile.write_text('\n'.join('"'+v.replace('\\','/')+'"' for v in values),encoding='utf-8')
subprocess.run([str(args.java_home/'bin/javac.exe'),'-J-Xmx256m','@'+str(argfile)],check=True)
entries={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')}
entries.update({f.relative_to(BASE/'resources').as_posix():f.read_bytes() for f in (BASE/'resources').rglob('*') if f.is_file()})
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as jar:
    for name,data in sorted(entries.items()):
        info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o644<<16
        jar.writestr(info,data,compresslevel=9)
result={'file':str(output),'sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'bytes':output.stat().st_size,'entries':sorted(entries)}
(work/'build-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result,indent=2))
