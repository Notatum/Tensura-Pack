"""Apply contextual Spanish corrections without modifying official Great Sage or code."""
from pathlib import Path
import argparse,json,re,zipfile

BASE=Path(__file__).resolve().parent;PACK=BASE.parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--english-sources',type=Path,action='append',default=[]);parser.add_argument('--report',type=Path,required=True)
args=parser.parse_args();english={}
for file in (PACK/'mods').glob('*.jar'):
    with zipfile.ZipFile(file) as archive:
        for name in archive.namelist():
            parts=name.split('/')
            if len(parts)==4 and parts[0]=='assets' and parts[2:] == ['lang','en_us.json']:
                english.setdefault(parts[1],{}).update(json.loads(archive.read(name).decode('utf-8-sig')))
for file in args.english_sources:
    for namespace,values in json.loads(file.read_text(encoding='utf-8-sig')).items():english.setdefault(namespace,{}).update(values)
reviewed=json.loads((BASE/'reviewed-corrections.json').read_text(encoding='utf-8'))
changes=[];translated={}
tokens=lambda text:re.findall(r'%(?:\d+\$)?[sdifegcSbBoOxXhHaAtTn%]',text)
for file in sorted(BASE.glob('*/es_es.json')):
    namespace=file.parent.name
    if namespace in {'greatsage','trnightmare','nightmareutils','textanimator'}:continue
    values=json.loads(file.read_text(encoding='utf-8'));updated=dict(values)
    for key,new in reviewed.get(namespace,{}).items():
        if key not in values:raise ValueError(f'Unknown reviewed key: {namespace}/{key}')
        updated[key]=new
    for key,old in values.items():
        text=updated[key];source=english.get(namespace,{}).get(key,'')
        # These substitutions are gated by the original English and the mod's context.
        text=re.sub(r'\bmagiculas\b','magículas',text);text=re.sub(r'\bMagiculas\b','Magículas',text)
        if re.search(r'\brace[s]?\b',source,re.I):
            for a,b in [('carreras','razas'),('Carreras','Razas'),('carrera','raza'),('Carrera','Raza')]:text=re.sub(r'\b'+a+r'\b',b,text)
        if namespace in {'tensura','openpartiesandclaims','xaeroworldmap'} and re.search(r'\bplayer\b',source,re.I):
            text=re.sub(r'\breproductor\b','jugador',text);text=re.sub(r'\bReproductor\b','Jugador',text)
        if re.search(r'\bslime\b',source,re.I):
            text=re.sub(r'\blimo\b','slime',text);text=re.sub(r'\bLimo\b','Slime',text)
        if namespace=='ascension' and re.search(r'\bstrain\b',source,re.I):
            text=re.sub(r'\bcepa\b','tensión',text);text=re.sub(r'\bCepa\b','Tensión',text)
        updated[key]=text
        if text!=old:
            if tokens(old)!=tokens(text):raise ValueError(f'Changed formatting tokens: {namespace}/{key}: {tokens(old)} -> {tokens(text)}')
            if re.findall('§.',old)!=re.findall('§.',text):raise ValueError(f'Changed color codes: {key}')
            changes.append({'mod':namespace,'key':key,'english':source,'previous':old,'spanish':text})
    if updated!=values:file.write_bytes((json.dumps(updated,ensure_ascii=False,indent=2)+'\n').encode('utf-8'))
    translated[namespace]=updated

retired={'trnightmare','nightmareutils','textanimator'}
def patch_archive(file,resource_pack=False):
    with zipfile.ZipFile(file) as source:
        entries=[];changed=False
        for info in source.infolist():
            name=info.filename;parts=name.split('/');data=source.read(info)
            namespace=parts[1] if len(parts)>1 and parts[0]=='assets' else ''
            if resource_pack and namespace in retired:changed=True;continue
            if len(parts)==4 and parts[2]=='lang' and parts[3] in {'es_es.json','es_ar.json','es_mx.json'} and namespace in translated:
                values=json.loads(data.decode('utf-8-sig'));updated=dict(values);updated.update(translated[namespace])
                if updated!=values:data=(json.dumps(updated,ensure_ascii=False,indent=2)+'\n').encode();changed=True
            entries.append((info,data))
        if resource_pack:
            names={info.filename for info,_ in entries}
            for namespace,values in translated.items():
                for locale in ['es_es','es_ar','es_mx']:
                    name=f'assets/{namespace}/lang/{locale}.json'
                    if name not in names:
                        info=zipfile.ZipInfo(name,(2026,11,2,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
                        entries.append((info,(json.dumps(values,ensure_ascii=False,indent=2)+'\n').encode()));changed=True
    if changed:
        temporary=file.with_suffix(file.suffix+'.reviewed')
        with zipfile.ZipFile(temporary,'w',zipfile.ZIP_DEFLATED) as output:
            for info,data in entries:output.writestr(info,data)
        with zipfile.ZipFile(temporary) as output:assert output.testzip() is None
        temporary.replace(file)
    return changed

jars=[]
for file in sorted((PACK/'mods').glob('*.jar')):
    if file.name.startswith('greatsage-'):continue
    if patch_archive(file):jars.append(file.name)
for file in sorted((PACK/'resourcepacks').glob('Tensura-Espanol-*.zip')):patch_archive(file,True)
report={'corrections':changes,'changedJars':jars,'count':len(changes),'mods':sorted({r['mod'] for r in changes})}
args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_bytes((json.dumps(report,ensure_ascii=False,indent=2)+'\n').encode())
print('Reviewed Spanish:',len(changes),'corrections across',len(report['mods']),'mods; updated',len(jars),'direct JARs. Official Great Sage untouched.')
