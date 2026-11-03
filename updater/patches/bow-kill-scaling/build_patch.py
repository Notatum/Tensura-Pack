from pathlib import Path
import argparse, hashlib, json, os, re, subprocess, zipfile

BASE = Path(__file__).resolve().parent
ROOT = BASE.parents[3]
INPUT_SHA256 = '9ac27d6713166e87a157b4e42f00753c12a0b127fd8d532ddc7b5520ac148c68'
TEXT = {
    'tooltip.beelzebuth_bow.damage': 'Daño principal cargado: %s',
    'tooltip.beelzebuth_bow.mode': 'Cono %s · daño plano %s · efecto %s%% (%s s)',
    'tooltip.beelzebuth_bow.beam': 'Láser instantáneo sin flechas · acumula el EP ganado por sus bajas',
    'message.beelzebuth_bow.ep': '+%s EP de la baja para el arco',
}


def languages(body):
    data = json.loads(body.decode('utf-8-sig'))
    assert all(key in data for key in TEXT)
    data.update(TEXT)
    return (json.dumps(data, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--original', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--resource-pack', type=Path, required=True)
    args = parser.parse_args()
    assert hashlib.sha256(args.original.read_bytes()).hexdigest() == INPUT_SHA256
    args.output_dir.mkdir(parents=True, exist_ok=True)
    classes = args.output_dir / 'classes'
    classes.mkdir(exist_ok=True)
    jars = list((ROOT / 'libraries').rglob('*.jar')) + list((ROOT / 'mods').glob('*.jar'))
    jars += list((ROOT / 'maintenance/upstream').glob('manascore-*-neoforge-4.0.0.2.jar'))
    sources = list((BASE / 'src').rglob('*.java')) + [BASE / 'PatchBowScaling.java']
    values = ['-proc:none', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, jars)), '-d', str(classes)] + list(map(str, sources))
    argfile = args.output_dir / 'javac.args'
    argfile.write_text('\n'.join('"' + value.replace('\\', '/') + '"' for value in values), encoding='utf-8')
    subprocess.run(['javac', '@' + str(argfile)], check=True)
    asm = [ROOT / f'libraries/org/ow2/asm/{name}/9.10.1/{name}-9.10.1.jar' for name in ['asm', 'asm-tree']]
    patch_cp = os.pathsep.join(map(str, [classes] + asm))
    replacement = {file.relative_to(classes).as_posix(): file.read_bytes() for file in (classes / 'ar').rglob('*.class')}
    output = args.output_dir / 'Arco-de-Beelzebuth-neoforge-1.21.1-1.4.8.jar'
    with zipfile.ZipFile(args.original) as source:
        for name in ['ar/juan/beelzebuthbow/BowProgress.class', 'ar/juan/beelzebuthbow/DevouringBowItem.class']:
            file = classes / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_bytes(source.read(name))
            subprocess.run(['java', '-cp', patch_cp, 'PatchBowScaling', str(file)], check=True)
            replacement[name] = file.read_bytes()
        with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as target:
            for entry in source.infolist():
                body = replacement.pop(entry.filename, source.read(entry))
                if entry.filename == 'META-INF/neoforge.mods.toml':
                    body = re.sub(r'(?m)^version\s*=\s*"1\.4\.6"', 'version="1.4.8"', body.decode('utf-8-sig'), count=1).encode('utf-8')
                if entry.filename.startswith('assets/beelzebuth_bow/lang/') and entry.filename.endswith('.json'):
                    body = languages(body)
                target.writestr(entry, body)
            for name, body in sorted(replacement.items()):
                target.writestr(name, body)
    with zipfile.ZipFile(args.original) as original, zipfile.ZipFile(output) as changed:
        assert changed.testzip() is None
        different = [name for name in original.namelist() if original.read(name) != changed.read(name)]
        allowed = {'ar/juan/beelzebuthbow/' + name + '.class' for name in ['BowProgress', 'DevouringBowItem', 'BowKillHandler', 'BowKillHandler$Pending']}
        allowed.add('META-INF/neoforge.mods.toml')
        assert all(name in allowed or name.startswith('assets/beelzebuth_bow/lang/') for name in different), different
        assert set(changed.namelist()) - set(original.namelist()) == {'ar/juan/beelzebuthbow/BowBalance.class'}
    resource = args.output_dir / args.resource_pack.name
    assert resource.resolve() != args.resource_pack.resolve()
    with zipfile.ZipFile(args.resource_pack) as original, zipfile.ZipFile(resource, 'w') as target:
        for entry in original.infolist():
            body = original.read(entry)
            if entry.filename in {f'assets/beelzebuth_bow/lang/{locale}.json' for locale in ['es_es', 'es_ar', 'es_mx']}:
                body = languages(body)
            target.writestr(entry, body)
    report = {'file': output.name, 'sha256': hashlib.sha256(output.read_bytes()).hexdigest(),
              'previous': args.original.name, 'previousSHA256': INPUT_SHA256, 'changedEntries': different,
              'basePerStage': 10, 'epDamageRate': '0.00002', 'killEpSource': 'actual shooter baseMaxEP gain',
              'playerKeepsKillEp': True}
    (args.output_dir / 'build-manifest.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
