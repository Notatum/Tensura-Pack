"""Refresh only the custom Beelzebuth description in the Spanish resource pack."""
from pathlib import Path
import json,zipfile
BASE=Path(__file__).resolve().parent;PACK=BASE.parents[2]
source=PACK/'updater/translations/beelzebuth/es_es.json'
key='beelzebuth.skill.beelzebuth.description';description=json.loads(source.read_text(encoding='utf-8'))[key]
path=PACK/'resourcepacks/Tensura-Espanol-2026.11.02.zip';temporary=path.with_suffix('.zip.new')
with zipfile.ZipFile(path) as archive:entries={n:archive.read(n) for n in archive.namelist()}
changed={}
for lang in ('es_es','es_ar','es_mx'):
    name=f'assets/beelzebuth/lang/{lang}.json';values=json.loads(entries[name]);values[key]=description
    changed[name]=(json.dumps(values,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
with zipfile.ZipFile(temporary,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for name,body in sorted(entries.items()):
        info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o644<<16
        archive.writestr(info,changed.get(name,body),compresslevel=9)
with zipfile.ZipFile(temporary) as archive:
    assert archive.testzip() is None
    for name,body in entries.items():
        if name not in changed:assert archive.read(name)==body
temporary.replace(path)
print('Updated only three custom Beelzebuth language entries; other resources preserved.')
