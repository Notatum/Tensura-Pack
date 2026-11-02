"""Orb 1.3.1: accept normal filesystem-length song names consistently, including networking."""
from pathlib import Path
import argparse,copy,hashlib,json,os,re,subprocess,zipfile
BASE=Path(__file__).resolve().parent;PACK=BASE.parents[2]
parser=argparse.ArgumentParser();parser.add_argument('--original',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);parser.add_argument('--server-root',type=Path,default=PACK.parent);args=parser.parse_args()
WORK=args.server_root/'maintenance/orb-audio-build';classes=WORK/'classes';classes.mkdir(parents=True,exist_ok=True)
libraries=[args.server_root/f'libraries/org/ow2/asm/{a}/9.10.1/{a}-9.10.1.jar' for a in ['asm','asm-tree']]
subprocess.run(['javac','-encoding','UTF-8','-cp',os.pathsep.join(map(str,libraries)),'-d',str(classes),str(BASE/'PatchOrbAudio.java')],check=True)
replacements={}
with zipfile.ZipFile(args.original) as source:
    for name in ['ar/juan/devouringorb/AudioRules.class','ar/juan/devouringorb/OrbNetwork.class','ar/juan/devouringorb/client/OrbSpeakerScreen.class']:
        file=classes/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(source.read(name))
        subprocess.run(['java','-cp',os.pathsep.join(map(str,[classes]+libraries)),'PatchOrbAudio',str(file)],check=True);replacements[name]=file.read_bytes()
    replacements['META-INF/neoforge.mods.toml']=re.sub(r'(?m)^version="1\.3\.0"','version="1.3.1"',source.read('META-INF/neoforge.mods.toml').decode('utf-8-sig'),count=1).encode()
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(args.output,'w',zipfile.ZIP_DEFLATED) as output:
        for info in source.infolist():output.writestr(copy.copy(info),replacements.get(info.filename,source.read(info)))
    with zipfile.ZipFile(args.output) as output:
        assert output.testzip() is None
        changed=[n for n in source.namelist() if source.read(n)!=output.read(n)]
        assert set(changed)==set(replacements),changed
print('Orb 1.3.1 built:',hashlib.sha256(args.output.read_bytes()).hexdigest())
