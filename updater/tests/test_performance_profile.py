from pathlib import Path
import json, os, subprocess, tempfile
PACK=Path(__file__).resolve().parents[2];ROOT=PACK.parent
BASE=ROOT/'maintenance/performance-profile-tests';BASE.mkdir(parents=True,exist_ok=True)
script=PACK/'updater/client/Preparar-Rendimiento.ps1'
checks=[]
with tempfile.TemporaryDirectory(prefix='performance-',dir=BASE) as directory:
    game=Path(directory);options=game/'options.txt';state=game/'.tensura-client-release.json';policy=game/'policy.json'
    data={'schema':1,'performance':{'revision':'test-1','optionCaps':{'entityDistanceScaling':1.0,'simulationDistance':6}}}
    policy.write_text(json.dumps(data));state.write_text('{}')
    def run(expect=0):
        result=subprocess.run(['powershell.exe','-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File',str(script),'-GameDir',str(game),'-PolicyFile',str(policy)],capture_output=True,text=True,encoding='utf-8',errors='replace',creationflags=subprocess.CREATE_NO_WINDOW)
        assert result.returncode==expect,result.stdout+result.stderr
    options.write_text('entityDistanceScaling:4.0\nsimulationDistance:10\nrenderDistance:29\nmaxFps:260\nkey_custom:key.keyboard.r\n',encoding='utf-8')
    run();text=options.read_text();assert 'entityDistanceScaling:1.0\n' in text and 'simulationDistance:6\n' in text;checks.append('400 percent entity distance and simulation 10 reduced')
    assert 'renderDistance:29\nmaxFps:260\nkey_custom:key.keyboard.r\n' in text;checks.append('render, FPS cap and keys preserved')
    assert state.read_text()=='{}';checks.append('script does not mark revision before updater success')
    options.write_text('entityDistanceScaling:0.5\nsimulationDistance:5\n');old=options.read_bytes();run();assert options.read_bytes()==old;checks.append('lighter settings remain exact')
    state.write_text(json.dumps({'performanceRevision':'test-1'}));options.write_text('entityDistanceScaling:3.0\nsimulationDistance:12\n');old=options.read_bytes();run();assert options.read_bytes()==old;checks.append('same revision preserves later customization')
    state.write_text('{}');options.write_text('entityDistanceScaling:4.0\nsimulationDistance:NaN\n');old=options.read_bytes();run(1);assert options.read_bytes()==old;checks.append('invalid option fails without partial write')
    data['performance']['optionCaps']['renderDistance']=8;policy.write_text(json.dumps(data));run(1);assert options.read_bytes()==old;checks.append('unapproved option rejected without write')
    del data['performance']['optionCaps']['renderDistance'];policy.write_text(json.dumps(data));options.unlink();run();assert 'simulationDistance:6' in options.read_text();checks.append('missing options initialized')
(BASE/'performance-results.json').write_text(json.dumps({'checks':checks,'errors':0},ensure_ascii=False,indent=2)+'\n')
for check in checks:print('PASS',check)
