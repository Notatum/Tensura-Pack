"""Update only race names, reset labels and seven missing evolution UI strings.

Keeps every official mod JAR unchanged. Repeatable on the current resource ZIP.
The Great Sage override is deliberately limited to these seven scene subtitles.
"""
from pathlib import Path
import json,zipfile

BASE=Path(__file__).resolve().parent
PACK=BASE.parents[1]
target=PACK/'resourcepacks/Tensura-Espanol-2026.11.02.zip'
nightmare=json.loads((BASE/'trnightmare/es_es.json').read_text(encoding='utf-8'))
race_names={k:v for k,v in nightmare.items() if k.startswith('trnightmare.race.') and k.count('.')==2}
assert all('§' not in value for value in race_names.values())
scene=json.loads((BASE/'greatsage-scene-es.json').read_text(encoding='utf-8'))
assert set(scene)=={f'greatsage.subtitle.scene.evolution.{n:02d}' for n in range(1,8)}
reset_keys=('tensura.command.reset_counter.check.awakening','tensura.command.reset_counter.check.race')
tensura=json.loads((BASE/'tensura/es_es.json').read_text(encoding='utf-8'))
with zipfile.ZipFile(target) as archive:
    entries={item.filename:(item,archive.read(item.filename)) for item in archive.infolist()}
changed={}
for locale in ('es_es','es_ar','es_mx'):
    for namespace,updates in (('trnightmare',race_names),('tensura',{k:tensura[k] for k in reset_keys}),('greatsage',scene)):
        name=f'assets/{namespace}/lang/{locale}.json'
        data=json.loads(entries[name][1]) if name in entries else {}
        # No stale full-namespace Great Sage replacement is allowed.
        if namespace=='greatsage': assert not (set(data)-set(scene))
        data.update(updates)
        changed[name]=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
temporary=target.with_suffix('.zip.new')
with zipfile.ZipFile(temporary,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as out:
    for name in sorted(set(entries)|set(changed)):
        info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o644<<16
        out.writestr(info,changed.get(name,entries[name][1] if name in entries else b''),compresslevel=9)
with zipfile.ZipFile(temporary) as check:
    assert check.testzip() is None
    for name,(_,body) in entries.items():
        if name not in changed: assert check.read(name)==body
temporary.replace(target)
print(f'Updated {len(changed)} language files; other resource entries unchanged.')
