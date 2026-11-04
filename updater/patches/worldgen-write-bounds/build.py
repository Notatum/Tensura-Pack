"""Build a reproducible standalone safety mod; outputs must remain in maintenance."""
from pathlib import Path
import argparse, hashlib, json, os, shutil, subprocess, zipfile

BASE = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument('--server-root', type=Path, required=True)
parser.add_argument('--work-dir', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--java-home', type=Path)
args = parser.parse_args()
root, work, output = args.server_root.resolve(), args.work_dir.resolve(), args.output.resolve()
private = root / 'maintenance'
assert work.is_relative_to(private) and output.is_relative_to(private), 'Build outputs must stay in private maintenance'
assert not work.is_relative_to(output), 'Output must not contain the build work directory'
work.mkdir(parents=True, exist_ok=True)
output.parent.mkdir(parents=True, exist_ok=True)
classes = work / 'classes'
if classes.exists():
    assert classes.resolve().is_relative_to(work), 'Unexpected generated-classes path'
    shutil.rmtree(classes)
classes.mkdir(exist_ok=True)
java_home = args.java_home
javac = str(java_home / 'bin/javac.exe') if java_home else shutil.which('javac')
java = str(java_home / 'bin/java.exe') if java_home else shutil.which('java')
assert javac and java, 'Java 21 JDK required'
ars = root / 'mods/ars_elemental-1.21.1-0.7.10.3.jar'
assert hashlib.sha256(ars.read_bytes()).hexdigest() == '0a286bcecb14ee7de12d8faeb74985659ec69e6351744e69cbcd260bd134b38d', 'Unexpected Ars Elemental input; review redirects first'
jars = sorted((root / 'libraries').rglob('*.jar')) + [ars]
arguments = ['-proc:none', '--release', '21', '-g:none', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, jars)), '-d', str(classes)]
arguments += [str(p) for p in sorted((BASE / 'src').rglob('*.java'))]
argfile = work / 'javac.args'
argfile.write_text('\n'.join('"' + v.replace('\\', '/') + '"' for v in arguments), encoding='utf-8')
subprocess.run([javac, '@' + str(argfile)], check=True)
subprocess.run([javac, '--release', '21', '-g:none', '-cp', str(classes), '-d', str(classes), str(BASE / 'tests/ChunkWriteLimitsTest.java')], check=True)
test = subprocess.run([java, '-cp', str(classes), 'ChunkWriteLimitsTest'], check=True, capture_output=True, text=True)
entries = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class') if p.relative_to(classes).parts[0] == 'local'}
entries.update({p.relative_to(BASE / 'resources').as_posix(): p.read_bytes() for p in (BASE / 'resources').rglob('*') if p.is_file()})
with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as jar:
    for name, body in sorted(entries.items()):
        info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o644 << 16
        jar.writestr(info, body, compresslevel=9)
with zipfile.ZipFile(output) as jar:
    assert jar.testzip() is None
result = {'file': str(output), 'sha256': hashlib.sha256(output.read_bytes()).hexdigest(), 'bytes': output.stat().st_size, 'entries': sorted(entries), 'boundsTest': test.stdout.strip(), 'originalArsPreserved': hashlib.sha256(ars.read_bytes()).hexdigest()}
(work / 'build-results.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps(result, indent=2))
