from pathlib import Path
import argparse
import hashlib
import json
import re
import subprocess
import sys
import zipfile

OUT = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument('--server-root', type=Path, default=OUT.parents[1])
parser.add_argument('--original-mods', type=Path, default=OUT / 'before/mods')
options = parser.parse_args()
ROOT = options.server_root.resolve()
sys.stdout.reconfigure(encoding='utf-8')
classes = OUT / 'classes'
classes.mkdir(exist_ok=True)
libraries = list((ROOT / 'libraries').rglob('*.jar'))
classpath = ';'.join(map(str, libraries + list((ROOT / 'mods').glob('*.jar')) + list((ROOT / 'maintenance/upstream').glob('manascore-*-neoforge-4.0.0.2.jar'))))
arguments = ['-proc:none', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(classes)] + [str(p) for p in (OUT / 'src').rglob('*.java')] + [str(OUT / 'PatchCustomItemSafety.java')]
argfile = OUT / 'javac.args'
argfile.write_text('\n'.join('"' + x.replace('\\', '/') + '"' for x in arguments), encoding='utf-8')
subprocess.run(['javac', '@' + str(argfile)], check=True)
asm = ROOT / 'libraries/org/ow2/asm/asm/9.10.1/asm-9.10.1.jar'
tree = ROOT / 'libraries/org/ow2/asm/asm-tree/9.10.1/asm-tree-9.10.1.jar'
patch_cp = ';'.join(map(str, [classes, asm, tree]))
targets = [
    ('dungeon', 'EP-Dungeon-neoforge-1.21.1-1.0.32.jar', 'EP-Dungeon-neoforge-1.21.1-1.0.33.jar', '1.0.33', ['ar/juan/epdungeon/BossFloor6Handler.class', 'ar/juan/epdungeon/DungeonConfig.class']),
    ('bow', 'Arco-de-Beelzebuth-neoforge-1.21.1-1.4.5.jar', 'Arco-de-Beelzebuth-neoforge-1.21.1-1.4.6.jar', '1.4.6', ['ar/juan/beelzebuthbow/DevouringBowItem.class']),
    ('orb', 'Orbe-de-Beelzebuth-neoforge-1.21.1-1.2.0.jar', 'Orbe-de-Beelzebuth-neoforge-1.21.1-1.2.1.jar', '1.2.1', []),
]
release = OUT / 'release/mods'
release.mkdir(parents=True, exist_ok=True)
manifest = []
for group, old, new, version, patch_classes in targets:
    source = options.original_mods / old
    replacements = {}
    with zipfile.ZipFile(source) as archive:
        for name in patch_classes:
            path = classes / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(archive.read(name))
            mode = 'config' if name.endswith('DungeonConfig.class') else group
            subprocess.run(['java', '-cp', patch_cp, 'PatchCustomItemSafety', mode, str(path)], check=True)
            replacements[name] = path.read_bytes()
        if group == 'bow':
            replacements['ar/juan/beelzebuthbow/BowFoodGuard.class'] = (classes / 'ar/juan/beelzebuthbow/BowFoodGuard.class').read_bytes()
        if group == 'orb':
            for name in ['OrbCombat', 'OrbFoodGuard', 'OrbDropOwnership']:
                replacements[f'ar/juan/devouringorb/{name}.class'] = (classes / f'ar/juan/devouringorb/{name}.class').read_bytes()
            replacements['ar/juan/devouringorb/mixin/ItemOwnershipAccessor.class'] = (classes / 'ar/juan/devouringorb/mixin/ItemOwnershipAccessor.class').read_bytes()
            replacements['ar/juan/devouringorb/mixin/ItemOwnershipMergeMixin.class'] = (classes / 'ar/juan/devouringorb/mixin/ItemOwnershipMergeMixin.class').read_bytes()
            replacements['devouring_orb.safety.mixins.json'] = (json.dumps({'required': True, 'minVersion': '0.8', 'package': 'ar.juan.devouringorb.mixin', 'compatibilityLevel': 'JAVA_21', 'mixins': ['ItemOwnershipAccessor', 'ItemOwnershipMergeMixin'], 'injectors': {'defaultRequire': 1}}, indent=2) + '\n').encode()
        metadata = archive.read('META-INF/neoforge.mods.toml').decode('utf-8-sig')
        metadata = re.sub(r'(?m)^version\s*=\s*"[^"]+"', 'version="' + version + '"', metadata, count=1)
        if group == 'orb':
            metadata += '\n[[mixins]]\nconfig="devouring_orb.safety.mixins.json"\n'
        replacements['META-INF/neoforge.mods.toml'] = metadata.encode('utf-8')
        destination = release / new
        with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as patched:
            for info in archive.infolist():
                patched.writestr(info, replacements.pop(info.filename, archive.read(info)))
            for name, body in replacements.items():
                patched.writestr(name, body)
        with zipfile.ZipFile(destination) as check:
            assert check.testzip() is None
    manifest.append({'previous': old, 'file': new, 'version': version, 'sha256': hashlib.sha256(destination.read_bytes()).hexdigest()})
(OUT / 'release/build-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
print('Built', len(manifest), 'fixed custom mods')
