"""Patch the pack's translated Ascension 3.0.1 without rebuilding unrelated content."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import zipfile

BASE = Path(__file__).resolve().parent
ROOT = BASE.parents[3]
SOURCE_SHA256 = '776089f951757af3b1882837f994d9ad0a23ff21ac4ad05e4faf7b4b0db8bf60'
TEXT = {
    'block.ascension.awakening_altar.desc': 'Colocá el altar, arrojá un Catalizador Definitivo encima y hacé clic derecho para despertar una habilidad.',
    'ascension.codex.awakening.body': 'Colocá un Altar del Despertar: no necesita piso especial ni pilares. Arrojá un Catalizador Definitivo encima y hacé clic derecho con la mano vacía. Necesitás 5.000.000 de EP máximos, ser Verdadero Señor Demonio o Verdadero Héroe y dominar una habilidad única compatible, además de sus requisitos particulares. El ritual dura 25 segundos; permanecé a 8 bloques horizontales o menos. Si tenés varias habilidades aptas, se elige una al azar. Obtenés su definitiva, se reemplaza la única y tus EP máximos aumentan un 25 %. Podés realizar otro despertar enseguida: no hay tiempo de espera entre rituales.',
    'ascension.codex.awakening_altar.body': 'Se fabrica con un núcleo de slime, dos lingotes de mithril y cuatro bloques de obsidiana llorosa. Colocá solo este bloque; no necesita una estructura alrededor. Arrojá un Catalizador Definitivo encima y hacé clic derecho con la mano vacía. No hay tiempo de espera entre despertares.',
    'block.ascension.deepslate_magic_block.desc': 'Pizarra profunda veteada de cristal, para construir y decorar.',
    'ascension.codex.deepslate_magic_block.body': 'Se fabrica con ladrillos de pizarra profunda y bloques de cristal mágico de alta calidad. Podés usarlo para construir y decorar.',
    'ascension.altar_preview.step1': 'Fabricá y colocá un Altar del Despertar. No necesita piso especial ni pilares.',
    'ascension.altar_preview.step2': 'Fabricá un Catalizador Definitivo con ocho Ecos del Alma y un lingote de hihiirokane.',
    'ascension.altar_preview.step3': 'Necesitás 5.000.000 de EP máximos, ser Verdadero Señor Demonio o Verdadero Héroe y dominar una habilidad única compatible.',
    'ascension.altar_preview.step4': 'Arrojá el catalizador encima del altar y hacé clic derecho con la mano vacía.',
    'ascension.altar_preview.step5': 'Permanecé cerca durante los 25 segundos del ritual. Podés volver a despertar otra habilidad enseguida, con otro catalizador.',
}


def rewrite_languages(source, output, prefix, locales):
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(output, 'w') as result:
        for entry in original.infolist():
            body = original.read(entry.filename)
            if entry.filename in {f'{prefix}/{locale}.json' for locale in locales}:
                data = json.loads(body.decode('utf-8-sig'))
                assert all(key in data for key in TEXT), entry.filename
                data.update(TEXT)
                body = (json.dumps(data, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
            result.writestr(entry, body)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--original', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--work-dir', type=Path, default=ROOT / 'maintenance/altar-simple-2026-11-03/build')
    parser.add_argument('--resource-pack', type=Path)
    parser.add_argument('--resource-output', type=Path)
    args = parser.parse_args()
    assert args.original.resolve() != args.output.resolve()
    assert hashlib.sha256(args.original.read_bytes()).hexdigest() == SOURCE_SHA256, 'Unexpected input JAR'
    args.work_dir.mkdir(parents=True, exist_ok=True)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    asm = [ROOT / f'libraries/org/ow2/asm/{name}/9.10.1/{name}-9.10.1.jar' for name in ['asm', 'asm-tree']]
    subprocess.run(['javac', '-proc:none', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, asm)),
                    '-d', str(args.work_dir), str(BASE / 'PatchSimpleAltar.java')], check=True)
    intermediate = args.work_dir / 'classes-patched.jar'
    subprocess.run(['java', '-cp', os.pathsep.join(map(str, [args.work_dir] + asm)), 'PatchSimpleAltar',
                    str(args.original), str(intermediate)], check=True)
    rewrite_languages(intermediate, args.output, 'assets/ascension/lang', ['en_us', 'es_es', 'es_ar', 'es_mx'])
    with zipfile.ZipFile(args.original) as original, zipfile.ZipFile(args.output) as result:
        assert original.namelist() == result.namelist()
        changed = [name for name in original.namelist() if original.read(name) != result.read(name)]
        expected = [f'com/simplygray/tensuraaddon/{name}.class' for name in ['handler/AwakeningRitual', 'AddonConfig']]
        expected += [f'assets/ascension/lang/{locale}.json' for locale in ['en_us', 'es_es', 'es_ar', 'es_mx']]
        assert set(changed) == set(expected), changed
        assert result.testzip() is None
    if args.resource_pack:
        assert args.resource_output and args.resource_pack.resolve() != args.resource_output.resolve()
        rewrite_languages(args.resource_pack, args.resource_output, 'assets/ascension/lang', ['es_es', 'es_ar', 'es_mx'])
    print('Changed entries:', changed)
    print('SHA256:', hashlib.sha256(args.output.read_bytes()).hexdigest())


if __name__ == '__main__':
    main()
