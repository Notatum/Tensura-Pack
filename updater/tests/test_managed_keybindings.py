"""Verify the distributed key profile, personal overrides and rejection of vanilla remaps."""
from pathlib import Path
import argparse,json,subprocess,tempfile

PACK=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser();parser.add_argument('--options',type=Path);args=parser.parse_args()
policy=json.loads((PACK/'config/tensura-keybindings.json').read_text(encoding='utf-8'))
script=PACK/'updater/client/Preparar-Teclas.ps1'
def parse(text):return dict(line.split(':',1) for line in text.splitlines() if ':' in line)
def run(game,file,expected=0):
    result=subprocess.run(['powershell.exe','-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File',str(script),'-GameDir',str(game),'-PolicyFile',str(file)],capture_output=True,encoding='utf-8',errors='replace')
    assert (result.returncode==0)==(expected==0),result.stdout+result.stderr
    return result.stdout
with tempfile.TemporaryDirectory(prefix='tensura-keybindings-') as temporary:
    game=Path(temporary);options=game/'options.txt';file=game/'policy.json';state=game/'.tensura-client-release.json'
    original=args.options.read_text(encoding='utf-8') if args.options else 'renderDistance:29\nkey_key.forward:key.keyboard.w\nkey_key.attack:key.mouse.left\nkey_tensura.keybinding.main_gui:key.keyboard.b\nkey_gui.xaero_new_waypoint:key.keyboard.b\nkey_key.greatsage.scan:key.keyboard.b\n'
    options.write_bytes(original.encode());file.write_text(json.dumps(policy),encoding='utf-8');before=parse(original)
    run(game,file);after=parse(options.read_text(encoding='utf-8'))
    assert all(after['key_'+name]==value for name,value in policy['bindings'].items())
    assert all(after[key]==value for key,value in before.items() if key not in {'key_'+k for k in policy['bindings']})
    print('PASS: every managed binding applied and all unrelated keys/options preserved')
    active=[value for value in policy['bindings'].values() if value!='key.keyboard.unknown']
    assert len(active)==len(set(active))
    for key in ['key.keyboard.b','key.keyboard.v','key.keyboard.g','key.keyboard.h','key.keyboard.n','key.keyboard.k','key.keyboard.c']:
        assert sum(value==key for name,value in after.items() if name.startswith('key_'))==1,(key,[(name,value) for name,value in after.items() if value==key])
    print('PASS: Tensura gameplay conflicts eliminated; managed active bindings are unique')
    state.write_text(json.dumps({'keybindingsRevision':policy['revision'],'greatSageVanillaRevision':'official','release':'test'}))
    custom=options.read_text().replace('key_tensura.keybinding.main_gui:key.keyboard.b','key_tensura.keybinding.main_gui:key.keyboard.home');options.write_bytes(custom.encode());old=options.read_bytes();old_state=state.read_bytes()
    run(game,file);assert options.read_bytes()==old and state.read_bytes()==old_state
    print('PASS: repeated update preserves personal Controlling edits and release state')
    updated=dict(policy);updated['revision']='test-new-revision';file.write_text(json.dumps(updated));run(game,file)
    assert parse(options.read_text())['key_tensura.keybinding.main_gui']=='key.keyboard.b'
    print('PASS: a new published profile revision applies to existing installations')
    for name,value in [('key.forward','key.keyboard.k'),('key.hotbar.1','key.keyboard.h'),('key.greatsage.menu','invalid')]:
        invalid=dict(updated);invalid['bindings']={name:value};file.write_text(json.dumps(invalid));old=options.read_bytes();run(game,file,expected=1);assert options.read_bytes()==old
    print('PASS: unsafe/vanilla remaps are rejected before options are written')
print('Managed keybindings verification: 0 errors')
