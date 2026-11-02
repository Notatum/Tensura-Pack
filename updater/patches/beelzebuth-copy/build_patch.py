"""Build Beelzebuth 1.3.8 from the verified 1.3.7 base and the reviewed sources."""
from pathlib import Path
import argparse, hashlib, json, os, re, subprocess, urllib.request, zipfile

BASE=Path(__file__).resolve().parent
PACK=BASE.parents[2]
parser=argparse.ArgumentParser()
parser.add_argument('--server-root',type=Path,default=PACK.parent)
parser.add_argument('--original',type=Path)
parser.add_argument('--output-dir',type=Path)
parser.add_argument('--include-learning',action='store_true',help='Build a test variant that unlocks target learning skills')
options=parser.parse_args()
ROOT=options.server_root.resolve()
WORK=options.output_dir.resolve() if options.output_dir else ROOT/'maintenance/beelzebuth-build'
WORK.mkdir(parents=True,exist_ok=True)
OLD='Beelzebuth-1.21.1-1.3.7-PersistentStomach-FastBlocks5x-MistFix-ES.jar'
EXPECTED='28f47dac07d56ff0b3ba5153d628fcad7468d31168ef31c92ff539832f3653b8'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
original=options.original or WORK/OLD
if not original.exists():
    local=ROOT/'mods'/OLD
    if local.exists():original=local
    else:
        url='https://raw.githubusercontent.com/Notatum/Tensura-Pack/04260de3e23c38484f5145bed24c27dc1ce5d8f1/mods/'+OLD
        urllib.request.urlretrieve(url,original)
if sha(original)!=EXPECTED:raise ValueError('The original Beelzebuth 1.3.7 does not match its published SHA-256')
classes=WORK/'classes';classes.mkdir(exist_ok=True)
jars=[original]+list((ROOT/'libraries').rglob('*.jar'))+list((ROOT/'mods').glob('*.jar'))+list((ROOT/'maintenance/upstream').glob('manascore-*-neoforge-4.0.0.2.jar'))
arguments=['-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes)]+[str(p) for p in sorted((BASE/'src').rglob('*.java'))]
argfile=WORK/'javac.args'
argfile.write_bytes(('\n'.join('"'+v.replace('\\','/')+'"' for v in arguments)+'\n').encode())
subprocess.run(['javac','@'+str(argfile)],check=True)
replacements={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
policy=json.loads((PACK/'config/beelzebuth-copy.json').read_text(encoding='utf-8'))
if options.include_learning:policy['includeLearningSkills']=True
replacements['META-INF/beelzebuth-copy.json']=(json.dumps(policy,indent=2)+'\n').encode()
translations=json.loads((PACK/'updater/translations/beelzebuth/es_es.json').read_text(encoding='utf-8'))
output=WORK/'Beelzebuth-1.21.1-1.3.8-InstantCopy-WorldVoice-ES.jar'
with zipfile.ZipFile(original) as source,zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as target:
    for entry in source.infolist():
        data=replacements.pop(entry.filename,source.read(entry))
        if entry.filename=='META-INF/neoforge.mods.toml':
            text=data.decode('utf-8-sig')
            text=re.sub(r'(?m)^version="1\.3\.7"','version="1.3.8"',text,count=1)
            text=text.replace('- El robo evita los mensajes y requisitos de adquisición UNLOCK_SKILL de cada habilidad para que los conjuntos grandes de resistencias no se transfieran una por una.',
                '- La copia conserva el tipo de instancia, la maestría y el progreso de modos, con disponibilidad en el mismo tick y sincronización por lote.\n- La copia admite a varios jugadores contra el mismo objetivo sin una espera compartida.\n- Las nuevas habilidades se anuncian mediante la Voz del Mundo de Great Sage, sin volver a cobrar costes de adquisición.\n- Los inventarios privados, los objetivos activos y los tiempos de reutilización del otro jugador no se transfieren.')
            text=text.replace('- Se corrigió la compatibilidad de PredatorMist con el renderizado de TR: Nightmare/Azathoth; el parche de velocidad ×5 ya no reemplaza el método heredado getSkill().','- PredatorMist conserva el método getSkill() de su clase base; el parche de velocidad ×5 respeta la identidad de la habilidad.')
            data=text.encode()
        if entry.filename.startswith('assets/beelzebuth/lang/es_') and entry.filename.endswith('.json'):
            values=json.loads(data.decode('utf-8-sig'));values.update(translations)
            data=(json.dumps(values,ensure_ascii=False,indent=2)+'\n').encode()
        target.writestr(entry,data)
    for name,data in sorted(replacements.items()):
        entry=zipfile.ZipInfo(name,(2026,11,2,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED
        target.writestr(entry,data)
with zipfile.ZipFile(output) as verify:assert verify.testzip() is None
(WORK/'build-manifest.json').write_bytes((json.dumps({'version':'1.3.8','previous':OLD,'baseSha256':EXPECTED,'file':output.name,'sha256':sha(output),'policy':policy},indent=2)+'\n').encode())
print('Built',output.name,sha(output))
