package ar.juan.devouringorb.client;

import ar.juan.devouringorb.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** All mutable playback/connection state belongs to the client thread. */
@EventBusSubscriber(modid="devouring_orb",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ServerAudioClient {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Orb music cache I/O");t.setDaemon(true);return t;});
    private static final Map<String,ServerTrack> tracks=new LinkedHashMap<>();
    private static final Map<String,Path> ready=new HashMap<>();
    private static final Map<UUID,OrbNetwork.Music> latest=new HashMap<>();
    private static final Set<UUID> buffering=new HashSet<>();
    private static final LinkedHashSet<String> queue=new LinkedHashSet<>();
    private static final Map<String,List<Runnable>> callbacks=new HashMap<>();
    private static final Set<String> failed=new HashSet<>();
    private static final Map<LocalFileSound,Integer> inactiveSince=new WeakHashMap<>();
    private static final Map<UUID,Integer> retryAfter=new HashMap<>(),retryAttempts=new HashMap<>();
    private static UUID server;private static AudioCacheFiles cache;
    private static boolean known,enabled,checking;private static int epoch,ticks,lastQuery=-100,lastChunk;
    private static long serial,previewToken;private static String checkingHash,previewName;
    private static AudioCacheFiles.Incoming incoming;private static long transfer;
    private static volatile Set<Path> protectedPaths=Set.of();
    private static final long PREFETCH_BYTES=96L*1024*1024;

    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->{
        ServerAudioNetwork.clientCatalog=ServerAudioClient::catalog;
        ServerAudioNetwork.clientChunk=ServerAudioClient::chunk;
        ServerAudioNetwork.clientFailure=ServerAudioClient::failure;
    });}
    private static Minecraft game(){return Minecraft.getInstance();}
    private static boolean connected(){return game().getConnection()!=null&&game().player!=null;}
    public static void tick(){
        ticks++;if(!connected())return;
        if(!known&&ticks-lastQuery>=40)query();
        if(incoming!=null&&ticks-lastChunk>400){String hash=incoming.track().sha256();cancelIncoming();fail(hash);}
        pump();
        if(enabled&&ticks%20==0)for(var m:new ArrayList<>(latest.values())){
            if(!m.playing()||!m.settings().loop()||LocalAudio.localPlaying(m.owner())||buffering.contains(m.owner())||ticks<retryAfter.getOrDefault(m.owner(),0)||retryAttempts.getOrDefault(m.owner(),0)>=2)continue;
            var t=tracks.get(m.settings().music());if(t==null||!ready.containsKey(t.sha256())||game().level==null)continue;
            var owner=game().level.getPlayerByUUID(m.owner());
            if(owner==null||!owner.isAlive()||owner.position().distanceToSqr(game().player.position())>m.settings().range()*m.settings().range())continue;
            if(OrbNetwork.heldMatching(owner,false,m.orb()).isEmpty()&&OrbNetwork.heldMatching(owner,true,m.orb()).isEmpty())continue;
            retryAttempts.merge(m.owner(),1,Integer::sum);deliver(m);
        }
    }
    public static void query(){if(connected()&&ticks-lastQuery>=20){lastQuery=ticks;PacketDistributor.sendToServer(new ServerAudioNetwork.Query());}}
    public static List<String> files(boolean music){
        if(!music)return AudioLibrary.localFiles(false);
        query();return known&&enabled?List.copyOf(tracks.keySet()):AudioLibrary.localFiles(true);
    }
    public static Path resolve(boolean music,String name)throws IOException{
        if(!music||!known||!enabled)return AudioLibrary.localResolve(music,name);
        ServerTrack track=tracks.get(name);Path path=track==null?null:ready.get(track.sha256());
        if(path==null)throw new IOException("Server song is not cached yet");return path;
    }
    private static void catalog(ServerAudioNetwork.Catalog message){
        if(known&&enabled==message.enabled()&&Objects.equals(server,message.server())&&List.copyOf(tracks.values()).equals(message.tracks()))return;
        var resume=new ArrayList<>(latest.values());
        for(var old:resume)if(old.playing())LocalAudio.localMusic(new OrbNetwork.Music(old.owner(),old.orb(),old.session(),old.settings(),false));
        cancelIncoming();epoch++;checking=false;cache=null;ready.clear();queue.clear();callbacks.clear();failed.clear();buffering.clear();
        known=true;enabled=message.enabled();server=message.server();tracks.clear();message.tracks().forEach(t->tracks.put(t.name(),t));
        refreshScreen();
        if(!enabled){for(var m:resume)if(m.playing())LocalAudio.localMusic(m);return;}
        int generation=epoch;UUID identity=server;Path directory=game().gameDirectory.toPath();
        IO.execute(()->{try{AudioCacheFiles created=new AudioCacheFiles(directory,identity);game().execute(()->{
            if(epoch!=generation)return;cache=created;
            for(var m:resume)if(m.playing()&&latest.get(m.owner())==m)music(m);
            long bytes=0;for(var t:tracks.values()){if(bytes+t.bytes()>PREFETCH_BYTES)continue;bytes+=t.bytes();enqueue(t,false,null);}pump();
        });}catch(Exception error){game().execute(()->{if(epoch==generation)report("Biblioteca del servidor");});}});
    }
    private static void enqueue(ServerTrack track,boolean priority,Runnable callback){
        if(callback!=null)callbacks.computeIfAbsent(track.sha256(),k->new ArrayList<>()).add(callback);
        if(ready.containsKey(track.sha256())){runCallbacks(track.sha256());return;}
        if(priority)failed.remove(track.sha256());
        if(failed.contains(track.sha256())){callbacks.remove(track.sha256());return;}
        if(incoming!=null&&incoming.track().sha256().equals(track.sha256())||Objects.equals(checkingHash,track.sha256())&&checking)return;
        if(priority){var old=new ArrayList<>(queue);queue.clear();queue.add(track.sha256());queue.addAll(old);}else queue.add(track.sha256());
        pump();
    }
    private static void pump(){
        if(!enabled||cache==null||checking||incoming!=null||queue.isEmpty()||!connected())return;
        String hash=queue.iterator().next();queue.remove(hash);ServerTrack track=tracks.values().stream().filter(t->t.sha256().equals(hash)).findFirst().orElse(null);if(track==null)return;
        checking=true;checkingHash=hash;int generation=epoch;AudioCacheFiles store=cache;
        IO.execute(()->{try{Path cached=store.verified(track);game().execute(()->{
            if(epoch!=generation)return;checking=false;checkingHash=null;
            if(cached!=null){ready.put(hash,cached);runCallbacks(hash);pump();}
            else{incoming=new AudioCacheFiles.Incoming(track);transfer=++serial;lastChunk=ticks;PacketDistributor.sendToServer(new ServerAudioNetwork.Request(hash,transfer,0));}
        });}catch(Exception error){game().execute(()->{if(epoch==generation){checking=false;checkingHash=null;fail(hash);}});}});
    }
    private static void chunk(ServerAudioNetwork.Chunk message){
        if(incoming==null||transfer!=message.transfer()||!incoming.track().sha256().equals(message.hash()))return;
        try{
            incoming.accept(message.offset(),message.data());lastChunk=ticks;
            if(incoming.complete()){
                if(!message.windowEnd())throw new IOException("Missing audio end marker");
                var complete=incoming;incoming=null;checking=true;checkingHash=message.hash();int generation=epoch;AudioCacheFiles store=cache;
                IO.execute(()->{try{Path cached=store.commit(complete.track(),complete.bytes(),()->protectedPaths);var evicted=store.takeEvicted();game().execute(()->{
                    if(epoch!=generation)return;checking=false;checkingHash=null;ready.entrySet().removeIf(e->evicted.contains(e.getValue()));ready.put(message.hash(),cached);runCallbacks(message.hash());pump();
                });}catch(Exception error){game().execute(()->{if(epoch==generation){checking=false;checkingHash=null;fail(message.hash());}});}});
            }else if(message.windowEnd())PacketDistributor.sendToServer(new ServerAudioNetwork.Request(message.hash(),transfer,incoming.offset()));
        }catch(Exception invalid){cancelIncoming();fail(message.hash());}
    }
    private static void cancelIncoming(){if(incoming!=null&&connected())PacketDistributor.sendToServer(new ServerAudioNetwork.Request(incoming.track().sha256(),transfer,-1));incoming=null;}
    private static void failure(ServerAudioNetwork.Failure message){if(incoming!=null&&transfer==message.transfer()&&incoming.track().sha256().equals(message.hash())){incoming=null;fail(message.hash());}}
    private static void fail(String hash){failed.add(hash);callbacks.remove(hash);queue.remove(hash);buffering.removeIf(id->{var m=latest.get(id);return m!=null&&Optional.ofNullable(tracks.get(m.settings().music())).map(ServerTrack::sha256).orElse("").equals(hash);});report(tracks.values().stream().filter(t->t.sha256().equals(hash)).map(ServerTrack::name).findFirst().orElse("Canción"));pump();}
    private static void runCallbacks(String hash){List<Runnable> list=callbacks.remove(hash);if(list!=null)list.forEach(Runnable::run);protect();}
    public static void music(OrbNetwork.Music message){
        OrbNetwork.Music old=latest.get(message.owner());if(old!=null&&old.session()>message.session())return;
        if(old==null||old.session()!=message.session()){retryAfter.remove(message.owner());retryAttempts.remove(message.owner());}
        latest.put(message.owner(),message);
        if(!message.playing()){buffering.remove(message.owner());LocalAudio.localMusic(message);protect();return;}
        if(!known){buffering.add(message.owner());query();return;}
        if(!enabled){buffering.remove(message.owner());LocalAudio.localMusic(message);return;}
        ServerTrack track=tracks.get(message.settings().music());
        if(track==null){buffering.remove(message.owner());report(message.settings().music());return;}
        if(ready.containsKey(track.sha256())){buffering.remove(message.owner());deliver(message);protect();return;}
        // Stop the previous session immediately while the replacement is downloading.
        LocalAudio.localMusic(new OrbNetwork.Music(message.owner(),message.orb(),message.session(),message.settings(),false));buffering.add(message.owner());
        enqueue(track,true,()->{if(latest.get(message.owner())==message&&connected()){buffering.remove(message.owner());deliver(message);}});
    }
    private static void deliver(OrbNetwork.Music message){LocalAudio.localMusic(message);retryAfter.put(message.owner(),ticks+100);}
    public static boolean finishedMusic(LocalFileSound sound){
        if(sound.isStopped()){inactiveSince.remove(sound);return true;}
        if(game().getSoundManager().isActive(sound)){inactiveSince.remove(sound);return false;}
        if(!enabled)return sound.age()>10;
        // Loading a world/device can temporarily leave a streaming channel inactive.
        int since=inactiveSince.computeIfAbsent(sound,k->ticks);if(ticks-since<100)return false;
        inactiveSince.remove(sound);sound.finish();return true;
    }
    public static boolean playing(UUID owner){return buffering.contains(owner)||LocalAudio.localPlaying(owner);}
    public static void preview(boolean music,String name,float volume,int stage){
        stopPreview();long token=previewToken;previewName=music?name:null;
        if(!music||!known||!enabled){LocalAudio.localPreview(music,name,volume,stage);return;}
        ServerTrack track=tracks.get(name);if(track==null){report(name);return;}
        enqueue(track,true,()->{if(token==previewToken&&connected()){LocalAudio.localPreview(true,name,volume,stage);protect();}});
    }
    public static void stopPreview(){previewToken++;previewName=null;LocalAudio.localStopPreview();protect();}
    public static void clear(){
        epoch++;known=false;enabled=false;server=null;cache=null;tracks.clear();ready.clear();latest.clear();buffering.clear();queue.clear();callbacks.clear();failed.clear();inactiveSince.clear();retryAfter.clear();retryAttempts.clear();incoming=null;checking=false;checkingHash=null;ticks=0;lastQuery=-100;protectedPaths=Set.of();stopPreview();LocalAudio.localClear();
    }
    private static void protect(){Set<Path> paths=new HashSet<>();for(var m:latest.values())if(m.playing()){var t=tracks.get(m.settings().music());if(t!=null&&ready.get(t.sha256())!=null)paths.add(ready.get(t.sha256()));}var t=tracks.get(previewName);if(t!=null&&ready.get(t.sha256())!=null)paths.add(ready.get(t.sha256()));protectedPaths=Set.copyOf(paths);}
    private static void report(String name){LocalAudio.report(name,"server audio");}
    private static void refreshScreen(){if(game().screen instanceof OrbSpeakerScreen screen&&screen.musicTab){screen.refresh();screen.status=Component.translatable("screen.devouring_orb.server_library",tracks.size());}}
    public static void decorate(OrbSpeakerScreen screen){
        if(!screen.musicTab||!known||!enabled)return;
        if(incoming!=null){String name=incoming.track().name();if(name.codePointCount(0,name.length())>28)name=name.substring(0,name.offsetByCodePoints(0,27))+"…";screen.status=Component.translatable("screen.devouring_orb.server_downloading",name,incoming.percent());}
        else if(checking)screen.status=Component.translatable("screen.devouring_orb.server_checking");
        else screen.status=Component.translatable("screen.devouring_orb.server_library",tracks.size());
    }
}
