"""Reproducibly replace only the synchronous pulse of audited Orb1.3.3."""
from pathlib import Path
import argparse,copy,hashlib,json,os,re,shutil,subprocess,zipfile
BASE=Path(__file__).resolve().parent
parser=argparse.ArgumentParser()
for key in ('server-root','original','work-dir','output'):parser.add_argument('--'+key,type=Path,required=True)
parser.add_argument('--java-home',type=Path);args=parser.parse_args()
root,work,output=args.server_root.resolve(),args.work_dir.resolve(),args.output.resolve()
assert work.is_relative_to(root/'maintenance') and output.is_relative_to(root/'maintenance')
expected='93bccee3a4f0751474e6d80daad6f07c92cf60b157f76efc5edda2d7ee73850b'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(args.original)==expected,'Expected audited Orb1.3.3, with XP/range/audio/item protections intact'
work.mkdir(parents=True,exist_ok=True);output.parent.mkdir(parents=True,exist_ok=True);classes=work/'classes';classes.mkdir(exist_ok=True)
stub=work/'stubs/ar/juan/devouringorb/OrbCombat.java';stub.parent.mkdir(parents=True,exist_ok=True)
stub.write_text('''package ar.juan.devouringorb;
import java.util.List;import net.minecraft.server.level.*;import net.minecraft.world.entity.LivingEntity;import net.minecraft.world.item.ItemStack;import net.minecraft.world.phys.*;
final class OrbCombat {
static FeedingBatch consumeGround(ServerLevel l,ServerPlayer p,Vec3 o,AABB a,int s,ItemStack i,List<Vec3> f){throw new AssertionError();}
static boolean isEnemy(ServerPlayer p,LivingEntity e){throw new AssertionError();}
static void evolved(ServerPlayer p,ItemStack i){throw new AssertionError();}
}''',encoding='utf-8')
java=str(args.java_home/'bin/java.exe') if args.java_home else shutil.which('java');javac=str(args.java_home/'bin/javac.exe') if args.java_home else shutil.which('javac')
mc=root/'libraries/net/minecraft/server/1.21.1-20240808.144430/server-1.21.1-20240808.144430-srg.jar'
jars=[mc,args.original]+sorted((root/'libraries').rglob('*.jar'))+sorted((root/'mods').glob('*.jar'))+sorted((root/'maintenance/upstream').glob('manascore-*.jar'))
values=['-proc:none','--release','21','-g:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes),str(stub),str(BASE/'PatchPulseBudget.java')]+[str(p) for p in sorted((BASE/'src').rglob('*.java'))]
argfile=work/'javac.args';argfile.write_text('\n'.join('"'+v.replace('\\','/')+'"' for v in values),encoding='utf-8');subprocess.run([javac,'-J-Xmx128m','@'+str(argfile)],check=True)
with zipfile.ZipFile(args.original) as old:
    combat=classes/'ar/juan/devouringorb/OrbCombat.class';combat.write_bytes(old.read('ar/juan/devouringorb/OrbCombat.class'))
    subprocess.run([java,'-Xmx64m','-cp',os.pathsep.join(map(str,[classes]+jars)),'PatchPulseBudget',str(combat)],check=True)
    meta,n=re.subn(r'(?m)^version\s*=\s*"1\.3\.3"','version="1.3.4"',old.read('META-INF/neoforge.mods.toml').decode('utf-8-sig'));assert n==1
    changed={'ar/juan/devouringorb/OrbCombat.class':combat.read_bytes(),'META-INF/neoforge.mods.toml':meta.encode()}
    for locale in ('en_us','es_es','es_ar','es_mx'):
        name=f'assets/devouring_orb/lang/{locale}.json'
        if name not in old.namelist():continue
        data=json.loads(old.read(name));data['message.devouring_orb.pulse_busy']='The previous pulses are still resolving.' if locale=='en_us' else 'Los pulsos anteriores todavía se están resolviendo.'
        data['message.devouring_orb.pulse_interrupted']='Pulse interrupted; unprocessed targets and drops remain in the world.' if locale=='en_us' else 'Pulso interrumpido; los blancos y drops pendientes siguen en el mundo.'
        changed[name]=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode()
    additions={p.relative_to(classes).as_posix():p.read_bytes() for p in (classes/'ar/juan/devouringorb').glob('OrbPulseWork*.class')}
    assert additions and not set(additions)&set(old.namelist())
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as new:
        for entry in old.infolist():new.writestr(copy.copy(entry),changed.get(entry.filename,old.read(entry)))
        for name,body in additions.items():
            entry=zipfile.ZipInfo(name,(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o644<<16;new.writestr(entry,body)
    with zipfile.ZipFile(output) as new:
        assert new.testzip() is None;assert set(new.namelist())==set(old.namelist())|set(additions)
        actual=[n for n in old.namelist() if old.read(n)!=new.read(n)];assert set(actual)==set(changed)
result={'file':output.name,'sha256':sha(output),'bytes':output.stat().st_size,'changedEntries':actual,'newEntries':list(additions),'allOtherEntriesByteIdentical':True,'globalTickBudgetNanos':6000000,'maxActionsPerTick':8}
(work/'build-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,indent=2))
