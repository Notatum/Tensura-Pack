"""Reproducible NoDeathEPLoss build, restricted to private maintenance outputs."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,zipfile

BASE=Path(__file__).resolve().parent
parser=argparse.ArgumentParser()
parser.add_argument('--server-root',type=Path,required=True)
parser.add_argument('--work-dir',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--java-home',type=Path)
args=parser.parse_args()
root,work,output=args.server_root.resolve(),args.work_dir.resolve(),args.output.resolve()
private=root/'maintenance'
assert work.is_relative_to(private) and output.is_relative_to(private),'Build outputs must stay in maintenance'
work.mkdir(parents=True,exist_ok=True);output.parent.mkdir(parents=True,exist_ok=True)
classes=work/'classes'
if classes.exists():
    assert classes.resolve().is_relative_to(work),'Unexpected generated-classes path'
    shutil.rmtree(classes)
classes.mkdir()
java_home=args.java_home
javac=str(java_home/'bin/javac.exe') if java_home else shutil.which('javac')
assert javac,'Java21 JDK required'
inputs={
 'tensura-neoforge-2.0.1.4.jar':'92c21507d1340de5d4565e54f88d4fb33179bc763af2a3f7e4e6262d0a352d79',
 'ascension-3.0.1.jar':'67ef150bafc1de42c15b9f5fa477ffba4f5ae168ab15d4943117ffb88698548c',
 'trnightmare-1.0.6.1-neoforge-1.21.1.jar':'7be16ab59637fe35052cedbeddcbff7142ffc769de10983868e560336a9ed212',
 'mysticism-neoforge-2.2.1-ES-Corregido-FixRaceInit.jar':'fd1626e7b14a713cbeda7d8a2bafde2968fe6bee5e5460e5a40a3082a8386c09',
}
for name,expected in inputs.items():
    assert hashlib.sha256((root/'mods'/name).read_bytes()).hexdigest()==expected,'Unexpected audited input: '+name
jars=[root/'libraries/net/minecraft/server/1.21.1-20240808.144430/server-1.21.1-20240808.144430-srg.jar']+sorted((root/'libraries').rglob('*.jar'))+sorted((root/'mods').glob('*.jar'))
arguments=['-proc:none','--release','21','-g:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes)]+[str(p) for p in sorted((BASE/'src').rglob('*.java'))]
argfile=work/'javac.args';argfile.write_text('\n'.join('"'+v.replace('\\','/')+'"' for v in arguments),encoding='utf-8')
subprocess.run([javac,'@'+str(argfile)],check=True)
entries={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
entries.update({p.relative_to(BASE/'resources').as_posix():p.read_bytes() for p in (BASE/'resources').rglob('*') if p.is_file()})
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as jar:
    for name,body in sorted(entries.items()):
        info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o644<<16
        jar.writestr(info,body,compresslevel=9)
with zipfile.ZipFile(output) as jar:assert jar.testzip() is None
result={'file':str(output),'sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'bytes':output.stat().st_size,'entries':sorted(entries),'auditedOriginalInputs':inputs}
(work/'build-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result,indent=2))
