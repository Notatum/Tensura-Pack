from pathlib import Path
import argparse, hashlib, os, subprocess, zipfile
BASE=Path(__file__).resolve().parent; ROOT=BASE.parents[3]
parser=argparse.ArgumentParser();parser.add_argument('--kind',choices=['starlight','mysticism'],required=True);parser.add_argument('--original',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
work=ROOT/'maintenance/client-crash-2026-11-02/race-patch-build';work.mkdir(parents=True,exist_ok=True)
asm=[ROOT/f'libraries/org/ow2/asm/{name}/9.10.1/{name}-9.10.1.jar' for name in ['asm','asm-tree']]
subprocess.run(['javac','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,asm)),'-d',str(work),str(BASE/'PatchRaceRequirements.java')],check=True)
args.output.parent.mkdir(parents=True,exist_ok=True)
subprocess.run(['java','-cp',os.pathsep.join(map(str,[work]+asm)),'PatchRaceRequirements',str(args.original),str(args.output),args.kind],check=True)
with zipfile.ZipFile(args.original) as old,zipfile.ZipFile(args.output) as new:
    assert old.namelist()==new.namelist()
    differences=[name for name in old.namelist() if old.read(name)!=new.read(name)]
    assert len(differences)==1,differences
    assert new.testzip() is None
print('Changed entries:',differences,'SHA256:',hashlib.sha256(args.output.read_bytes()).hexdigest())
