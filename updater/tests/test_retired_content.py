"""Exercise offline retirement, fixed palette indices, rollback and failure isolation (Java 21)."""
from pathlib import Path
import argparse,gzip,hashlib,struct,subprocess,tempfile,zlib

parser=argparse.ArgumentParser();parser.add_argument('--helper',type=Path,default=Path(__file__).resolve().parents[1]/'shared/RetiredContentMigration.jar');args=parser.parse_args()
HELPER=args.helper.resolve();passed=0
def check(condition,label):
    global passed
    assert condition,label;passed+=1;print('PASS:',label,flush=True)
def t(kind,value):return (kind,value)
def compound(**values):return t(10,values)
def txt(value):return t(8,value)
def sequence(kind,*values):return t(9,(kind,list(values)))
def string(s):
    data=s.encode('utf-8');return struct.pack('>H',len(data))+data
def payload(tag):
    kind,value=tag
    if kind in {1,2,3,4,5,6}:return struct.pack('>'+{1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[kind],value)
    if kind==8:return string(value)
    if kind==9:return bytes([value[0]])+struct.pack('>i',len(value[1]))+b''.join(payload(v) for v in value[1])
    if kind==10:return b''.join(bytes([v[0]])+string(k)+payload(v) for k,v in value.items())+b'\0'
    if kind==12:return struct.pack('>i',len(value))+b''.join(struct.pack('>q',v) for v in value)
    raise ValueError(kind)
def nbt(tag):return b'\x0a\0\0'+payload(tag)
def hashes(root):return {p.relative_to(root).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file() and not p.relative_to(root).parts[0].startswith('backup')}
def run(action,root,backup,expected=0):
    result=subprocess.run(['java','-Dstdout.encoding=UTF-8','-jar',str(HELPER),action,str(root),str(backup)],capture_output=True,encoding='utf-8',errors='replace')
    assert (result.returncode==0)==(expected==0),result.stdout+result.stderr
    return result.stdout+result.stderr

with tempfile.TemporaryDirectory(prefix='tensura-retirement-') as tmp:
    root=Path(tmp);world=root/'saves/world';world.mkdir(parents=True);backup=root/'backup1'
    level=compound(Data=compound(SpawnX=t(3,20),SpawnY=t(3,70),SpawnZ=t(3,-30),WorldGenSettings=compound(dimensions=t(10,{'minecraft:overworld':compound(type=txt('minecraft:overworld')),'trnightmare:inner_world':compound(type=txt('trnightmare:inner_world'))}))))
    player=compound(Dimension=txt('trnightmare:inner_world'),Pos=sequence(6,t(6,0.),t(6,0.),t(6,0.)),race_key=compound(race=txt('trnightmare:serpent_zinnia_slime')),EP=t(6,123456789.75),Inventory=sequence(10,compound(id=txt('devouring_orb:devouring_orb'),components=compound(points=txt('10000000.75'))),compound(id=txt('trnightmare:storm_dragon_seed'))),skills=sequence(10,compound(skill=txt('beelzebuth:beelzebuth'),Mastery=t(6,10000.)),compound(skill=txt('trnightmare:handler'))),HandItems=sequence(10,compound(id=txt('trnightmare:some_item')),compound(id=txt('minecraft:bow'))),custom=t(10,{'trnightmare:custom':txt('remove'),'personal':txt('keep')}))
    (world/'level.dat').write_bytes(gzip.compress(nbt(level)));(world/'playerdata').mkdir();(world/'playerdata/player.dat').write_bytes(gzip.compress(nbt(player)))
    retired=world/'dimensions/trnightmare/inner_world/region';retired.mkdir(parents=True);(retired/'private.dat').write_bytes(b'quarantined dimension')
    (root/'config/nightmare').mkdir(parents=True);(root/'config/nightmare/nightmare.toml').write_text('retired = true')
    chunk=compound(sections=sequence(10,compound(block_states=compound(palette=sequence(10,compound(Name=txt('minecraft:stone')),compound(Name=txt('trnightmare:nightmare_block'))),data=t(12,[12345])),biomes=compound(palette=sequence(8,txt('minecraft:plains'),txt('trnightmare:nightmare_biome'))))),Entities=sequence(10,compound(id=txt('trnightmare:nightmare_entity')),compound(id=txt('minecraft:zombie'))))
    compressed=zlib.compress(nbt(chunk));header=bytearray(8192);struct.pack_into('>I',header,0,(2<<8)|1)
    # A valid final sector may omit its unused padding, just as RegionFile does.
    (world/'region').mkdir();(world/'region/r.0.0.mca').write_bytes(header+struct.pack('>I',len(compressed)+1)+bytes([2])+compressed)
    (world/'region/empty.mca').write_bytes(b'')
    (world/'advancements').mkdir();(world/'advancements/player.json').write_text('{"DataVersion":3955,"trnightmare:retired":{"done":true},"minecraft:story/root":{"done":false}}')
    original=hashes(root);run('prepare-client',root,backup)
    check(hashes(root)==original,'Prepare leaves every original byte unchanged')
    run('apply',root,backup)
    changed=gzip.decompress((world/'playerdata/player.dat').read_bytes())
    check(b'trnightmare:' not in changed and b'nightmareutils:' not in changed,'Retired player registry entries are removed')
    check(b'tensura:slime' in changed and b'minecraft:overworld' in changed,'Removed slime race and dimension receive valid fallbacks')
    check(struct.pack('>d',123456789.75) in changed and b'10000000.75' in changed and b'beelzebuth:beelzebuth' in changed,'Personal EP, exact weapon EP and custom Beelzebuth survive')
    check(b'personal' in changed and b'keep' in changed,'Unrelated persistent data survives')
    check('trnightmare:' not in (world/'advancements/player.json').read_text() and 'minecraft:story/root' in (world/'advancements/player.json').read_text(),'Vanilla JSON progress loses only retired advancements')
    check(b'HandItems\x0a\0\0\0\x02' in changed,'Fixed hand slots retain both positions')
    b=(world/'region/r.0.0.mca').read_bytes();offset=(struct.unpack_from('>I',b)[0]>>8)*4096;length=struct.unpack_from('>I',b,offset)[0];decoded=zlib.decompress(b[offset+5:offset+4+length])
    check(b'trnightmare:' not in decoded and b'minecraft:air' in decoded and b'minecraft:zombie' in decoded,'Regions replace retired blocks and keep unrelated entities')
    check(struct.pack('>q',12345) in decoded,'Packed block-state indices remain unchanged')
    check(not (retired/'private.dat').exists() and not (root/'config/nightmare/nightmare.toml').exists(),'Removed dimensions and configs are quarantined with originals backed up')
    after=hashes(root);run('prepare-client',root,root/'backup2');run('apply',root,root/'backup2')
    check(hashes(root)==after,'Repeated migration is idempotent')
    run('rollback',root,backup)
    check(hashes(root)==original,'Rollback restores every original file byte for byte')
    # A corrupt world prevents changes to all worlds, rather than leaving a partial migration.
    bad=root/'saves/broken';bad.mkdir();(bad/'level.dat').write_bytes(b'not NBT')
    before=hashes(root);run('prepare-client',root,root/'backup3',expected=1)
    check(hashes(root)==before,'Malformed NBT aborts preparation without changing any world')
print('Retirement verification:',passed,'checks, 0 errors')
