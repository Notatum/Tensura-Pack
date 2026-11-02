"""Apply Spanish cosmetic rendering without translating IDs or cosmetic choice values."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,zipfile

BASE=Path(__file__).resolve().parent
PACK=BASE.parents[2]
parser=argparse.ArgumentParser()
parser.add_argument('--server-root',type=Path,default=PACK.parent)
parser.add_argument('--original',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
options=parser.parse_args();ROOT=options.server_root.resolve()
WORK=ROOT/'maintenance/nightmare-cosmetics-spanish-build';classes=WORK/'classes';classes.mkdir(parents=True,exist_ok=True)
asm=ROOT/'libraries/org/ow2/asm/asm/9.10.1/asm-9.10.1.jar'
tree=ROOT/'libraries/org/ow2/asm/asm-tree/9.10.1/asm-tree-9.10.1.jar'
analysis=ROOT/'libraries/org/ow2/asm/asm-analysis/9.10.1/asm-analysis-9.10.1.jar'
libraries=[asm,tree,analysis]
sources=list((BASE/'src').rglob('*.java'))+list(BASE.glob('*.java'))
arguments=['-encoding','UTF-8','-cp',os.pathsep.join(map(str,libraries)),'-d',str(classes)]+list(map(str,sources))
argfile=WORK/'javac.args';argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in arguments),encoding='utf-8')
subprocess.run(['javac','@'+str(argfile)],check=True)
target='com/github/hvnbael/trnightmare/client/screen/CosmeticScreen.class'
bridge='com/github/hvnbael/trnightmare/client/screen/CosmeticsSpanish.class'
with zipfile.ZipFile(options.original) as original:
    if bridge in original.namelist():raise ValueError('The input already contains the cosmetics translation adapter')
    file=classes/target;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(original.read(target))
    runtime_cp=os.pathsep.join(map(str,[classes]+libraries))
    subprocess.run(['java','-cp',runtime_cp,'PatchCosmeticsDisplay',str(file)],check=True)
    subprocess.run(['java','-Dstdout.encoding=UTF-8','-cp',runtime_cp,'VerifyCosmeticsPatch',str(file)],check=True)
    options.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(options.output,'w',zipfile.ZIP_DEFLATED) as output:
        for info in original.infolist():output.writestr(info,file.read_bytes() if info.filename==target else original.read(info))
        info=zipfile.ZipInfo(bridge,(2026,11,2,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;output.writestr(info,(classes/bridge).read_bytes())
with zipfile.ZipFile(options.original) as original,zipfile.ZipFile(options.output) as output:
    assert output.testzip() is None
    changed=[name for name in original.namelist() if original.read(name)!=output.read(name)]
    assert changed==[target],changed
    assert set(output.namelist())-set(original.namelist())=={bridge}
manifest={'baseSha256':hashlib.sha256(options.original.read_bytes()).hexdigest(),'sha256':hashlib.sha256(options.output.read_bytes()).hexdigest(),'modifiedClasses':changed,'addedClasses':[bridge],'registryAndSavedChoiceValuesUnchanged':True}
(WORK/'build-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps(manifest,indent=2))
