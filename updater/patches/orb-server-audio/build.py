"""Add a server music library to exact Orb 1.3.6, without rebuilding gameplay classes."""
from pathlib import Path
import argparse,copy,hashlib,json,os,re,subprocess,zipfile
BASE=Path(__file__).resolve().parent
p=argparse.ArgumentParser()
for name in ('server-root','original','work-dir','output','java-home'):p.add_argument('--'+name,type=Path,required=True)
a=p.parse_args();root=a.server_root.resolve();work=a.work_dir.resolve();output=a.output.resolve()
assert work.is_relative_to(root/'maintenance') and output.is_relative_to(root/'maintenance')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(a.original)=='850b905898c3ed96da7beacd7e0e1dc512fffc427be1e7a48539d0631cb0e043'
work.mkdir(parents=True,exist_ok=True);output.parent.mkdir(parents=True,exist_ok=True)
classes=work/'classes';classes.mkdir(exist_ok=True);stubs=work/'stubs';stubs.mkdir(exist_ok=True)
definitions={
'ar/juan/devouringorb/OrbSpeaker.java':'package ar.juan.devouringorb; public class OrbSpeaker {public static void localControl(net.minecraft.server.level.ServerPlayer p,net.minecraft.world.item.ItemStack s,boolean play){}}',
'ar/juan/devouringorb/client/AudioLibrary.java':'package ar.juan.devouringorb.client; public class AudioLibrary {public static java.util.List<String> localFiles(boolean m){return null;} public static java.nio.file.Path localResolve(boolean m,String n)throws java.io.IOException{return null;}}',
'ar/juan/devouringorb/client/LocalAudio.java':'package ar.juan.devouringorb.client; public class LocalAudio {public static void localMusic(ar.juan.devouringorb.OrbNetwork.Music m){} public static void localPreview(boolean m,String n,float v,int s){} public static boolean localPlaying(java.util.UUID u){return false;} public static void localStopPreview(){} public static void localClear(){} static void report(String f,String r){}}',
'ar/juan/devouringorb/client/OrbSpeakerScreen.java':'package ar.juan.devouringorb.client; public class OrbSpeakerScreen extends net.minecraft.client.gui.screens.Screen {boolean musicTab; net.minecraft.network.chat.Component status; public OrbSpeakerScreen(){super(net.minecraft.network.chat.Component.empty());} void refresh(){}}'
}
for name,body in definitions.items():f=stubs/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(body,encoding='utf-8')
client=root/'maintenance/managed-client-2026-11-02/client-test/game/libraries/net/minecraft/client/1.21.1-20240808.144430/client-1.21.1-20240808.144430-srg.jar'
server=root/'libraries/net/minecraft/server/1.21.1-20240808.144430/server-1.21.1-20240808.144430-srg.jar'
jars=[client,server,a.original]+sorted((root/'libraries').rglob('*.jar'))+sorted((root/'mods').glob('*.jar'))+sorted((root/'maintenance/upstream').glob('manascore-*.jar'))
cp=os.pathsep.join(map(str,jars));asm=[p for p in jars if p.name in ('asm-9.10.1.jar','asm-tree-9.10.1.jar')]
def javac(sources,classpath,dest):
    dest.mkdir(parents=True,exist_ok=True)
    values=['-proc:none','--release','21','-g:none','-encoding','UTF-8','-cp',classpath,'-d',str(dest)]+list(map(str,sources))
    args=work/'javac.args';args.write_text('\n'.join('"'+v.replace('\\','/')+'"' for v in values),encoding='utf-8')
    subprocess.run([str(a.java_home/'bin/javac.exe'),'-J-Xmx256m','@'+str(args)],check=True)
javac(sorted((BASE/'src').rglob('*.java'))+sorted(stubs.rglob('*.java')),cp,classes)
stubclasses={n.removesuffix('.java')+'.class' for n in definitions}
generated={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class') if f.relative_to(classes).as_posix() not in stubclasses}
patcher=work/'patcher';javac([BASE/'PatchServerAudio.java'],os.pathsep.join(map(str,asm)),patcher)
changed={};hooks=['ar/juan/devouringorb/OrbNetwork.class','ar/juan/devouringorb/OrbSpeaker.class','ar/juan/devouringorb/client/AudioLibrary.class','ar/juan/devouringorb/client/LocalAudio.class','ar/juan/devouringorb/client/OrbSpeakerScreen.class']
with zipfile.ZipFile(a.original) as old:
    for name in hooks:
        f=work/'patched'/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(old.read(name))
        subprocess.run([str(a.java_home/'bin/java.exe'),'-Xmx128m','-cp',os.pathsep.join([str(patcher)]+list(map(str,asm))),'PatchServerAudio',str(f)],check=True);changed[name]=f.read_bytes()
    meta,n=re.subn(r'(?m)^version\s*=\s*"1\.3\.6"','version="1.4.0"',old.read('META-INF/neoforge.mods.toml').decode('utf-8-sig'));assert n==1;changed['META-INF/neoforge.mods.toml']=meta.encode()
    for locale in ('en_us','es_ar','es_es','es_mx'):
        name='assets/devouring_orb/lang/'+locale+'.json';lang=json.loads(old.read(name));english=locale=='en_us'
        lang.update({
            'menu.devouring_orb.server_local_folder':'Local folder' if english else 'Carpeta local',
            'screen.devouring_orb.server_library':'Server library: %s songs' if english else 'Biblioteca del servidor: %s canciones',
            'screen.devouring_orb.server_downloading':'Downloading: %s (%s%%)' if english else 'Descargando: %s (%s%%)',
            'screen.devouring_orb.server_checking':'Checking server music cache...' if english else 'Verificando caché de música...',
            'message.devouring_orb.server_song_missing':'Choose a song from the server library (Refresh).' if english else 'Elegí una canción de la biblioteca del servidor (Actualizar).'
        });changed[name]=(json.dumps(lang,ensure_ascii=False,indent=2)+'\n').encode()
    assert not set(generated)&set(old.namelist())
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as new:
        for entry in old.infolist():new.writestr(copy.copy(entry),changed.get(entry.filename,old.read(entry)))
        for name,body in sorted(generated.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o644<<16;new.writestr(info,body)
    with zipfile.ZipFile(output) as new:
        assert new.testzip() is None;actual=[n for n in old.namelist() if old.read(n)!=new.read(n)];assert set(actual)==set(changed)
result={'file':output.name,'sha256':sha(output),'bytes':output.stat().st_size,'changedEntries':actual,'newEntries':sorted(generated),'allOtherEntriesByteIdentical':True}
(work/'build-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,indent=2))
