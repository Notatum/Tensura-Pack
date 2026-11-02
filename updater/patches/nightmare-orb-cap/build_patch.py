from pathlib import Path
import argparse,copy,hashlib,os,subprocess,zipfile
BASE=Path(__file__).resolve().parent;PACK=BASE.parents[2]
parser=argparse.ArgumentParser();parser.add_argument('--original',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);parser.add_argument('--server-root',type=Path,default=PACK.parent);args=parser.parse_args()
work=args.server_root/'maintenance/nightmare-orb-cap-build';classes=work/'classes';classes.mkdir(parents=True,exist_ok=True)
libraries=[args.original]+list((args.server_root/'libraries').rglob('*.jar'))
argfile=work/'javac.args';values=['-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,libraries)),'-d',str(classes),str(BASE/'OrbCapCompatibility.java'),str(BASE/'PatchOrbCap.java')]
argfile.write_text('\n'.join('"'+v.replace('\\','/')+'"' for v in values),encoding='utf-8');subprocess.run(['javac','@'+str(argfile)],check=True)
target='com/github/hvnbael/trnightmare/feature/combat/DamageCapService.class';helper='com/github/hvnbael/trnightmare/feature/combat/OrbCapCompatibility.class'
with zipfile.ZipFile(args.original) as source:
    file=classes/target;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(source.read(target))
    asm=[args.server_root/f'libraries/org/ow2/asm/{a}/9.10.1/{a}-9.10.1.jar' for a in ['asm','asm-tree']]
    subprocess.run(['java','-cp',os.pathsep.join(map(str,[classes]+asm)),'PatchOrbCap',str(file)],check=True)
    with zipfile.ZipFile(args.output,'w',zipfile.ZIP_DEFLATED) as output:
        for info in source.infolist():output.writestr(copy.copy(info),file.read_bytes() if info.filename==target else source.read(info))
        info=zipfile.ZipInfo(helper,(2026,11,2,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;output.writestr(info,(classes/helper).read_bytes())
    with zipfile.ZipFile(args.output) as output:
        assert output.testzip() is None
        assert [n for n in source.namelist() if source.read(n)!=output.read(n)]==[target]
print('Nightmare orb compatibility:',hashlib.sha256(args.output.read_bytes()).hexdigest())
