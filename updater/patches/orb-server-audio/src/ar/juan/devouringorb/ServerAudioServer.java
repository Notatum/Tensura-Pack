package ar.juan.devouringorb;

import java.util.*;
import java.util.concurrent.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Main-thread transfer state; the single worker does every filesystem operation. */
@EventBusSubscriber(modid="devouring_orb")
public final class ServerAudioServer {
    private static final Logger LOG=LoggerFactory.getLogger("OrbServerAudio");
    public static final int CHUNKS_PER_TICK=8;
    private static State state;
    private static final class Transfer {
        final long id;final ServerAudioFiles.Source source;int offset,lastTick;boolean loading,waiting;
        final ArrayDeque<byte[]> prepared=new ArrayDeque<>();
        Transfer(long id,ServerAudioFiles.Source source,int tick){this.id=id;this.source=source;lastTick=tick;}
    }
    private static final class State {
        final MinecraftServer server;
        final ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),r->{Thread t=new Thread(r,"Orb server audio I/O");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
        final ConcurrentLinkedQueue<Runnable> completions=new ConcurrentLinkedQueue<>();
        final Map<UUID,Transfer> transfers=new LinkedHashMap<>();final Map<UUID,Integer> queries=new HashMap<>();
        ServerAudioFiles files;ServerAudioFiles.Catalog catalog;int tick,lastScan=-200,rotation;boolean scanning;
        State(MinecraftServer server){this.server=server;}
        boolean submit(Runnable work){try{io.execute(work);return true;}catch(RejectedExecutionException busy){LOG.debug("Audio I/O queue full");return false;}}
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){
        if(!event.getServer().isDedicatedServer())return;
        State s=new State(event.getServer());state=s;scan(s);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){State s=state;if(s!=null&&s.server==event.getServer()){state=null;s.io.shutdownNow();s.completions.clear();s.transfers.clear();}}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer p)query(p);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){State s=state;if(s!=null){s.transfers.remove(event.getEntity().getUUID());s.queries.remove(event.getEntity().getUUID());}}
    public static void query(ServerPlayer player){
        State s=state;
        if(s==null||s.server!=player.getServer()){PacketDistributor.sendToPlayer(player,new ServerAudioNetwork.Catalog(false,new UUID(0,0),List.of()));return;}
        Integer last=s.queries.put(player.getUUID(),s.tick);if(last!=null&&s.tick-last<10)return;
        if(s.catalog!=null)sendCatalog(s,player);
        if(s.tick-s.lastScan>=20)scan(s);
    }
    private static void sendCatalog(State s,ServerPlayer p){PacketDistributor.sendToPlayer(p,new ServerAudioNetwork.Catalog(true,s.catalog.serverId(),s.catalog.tracks()));}
    private static void scan(State s){
        if(s.scanning||state!=s)return;s.scanning=true;s.lastScan=s.tick;
        if(!s.submit(()->{
            try{
                if(s.files==null)s.files=new ServerAudioFiles(s.server.getServerDirectory());
                var catalog=s.files.scan();
                s.completions.add(()->{s.scanning=false;boolean changed=s.catalog==null||!s.catalog.tracks().equals(catalog.tracks());s.catalog=catalog;if(changed){LOG.info("Orb server library: {} songs",catalog.sources().size());for(var p:s.server.getPlayerList().getPlayers())sendCatalog(s,p);}});
            }catch(Exception error){s.completions.add(()->{s.scanning=false;LOG.warn("Cannot scan orb server music library",error);});}
        }))s.scanning=false;
    }
    public static void control(ServerPlayer player,ItemStack orb,boolean play){
        State s=state;
        if(play&&s!=null&&s.server==player.getServer()){
            String name=OrbAudioSettings.read(orb).music();
            if(s.catalog==null||s.catalog.tracks().stream().noneMatch(t->t.name().equals(name))){
                OrbSpeaker.localControl(player,orb,false);player.displayClientMessage(Component.translatable("message.devouring_orb.server_song_missing"),false);query(player);return;
            }
        }
        OrbSpeaker.localControl(player,orb,play);
    }
    public static void request(ServerPlayer player,ServerAudioNetwork.Request message){
        State s=state;if(s==null||s.server!=player.getServer()||s.catalog==null)return;
        Transfer t=s.transfers.get(player.getUUID());
        if(message.offset()==-1){if(t!=null&&t.id==message.transfer()&&t.source.track().sha256().equals(message.hash()))s.transfers.remove(player.getUUID());return;}
        if(t==null){
            var source=s.catalog.find(message.hash());
            if(source==null||message.offset()>=source.track().bytes()||message.offset()%ServerTrack.CHUNK_BYTES!=0){PacketDistributor.sendToPlayer(player,new ServerAudioNetwork.Failure(message.hash(),message.transfer()));return;}
            // A client can stall while loading its world/shaders. Resume by verified hash
            // even if its idle transfer expired, without restarting the whole song.
            t=new Transfer(message.transfer(),source,s.tick);t.offset=message.offset();s.transfers.put(player.getUUID(),t);
        }else if(t.id!=message.transfer()||!t.source.track().sha256().equals(message.hash())||!t.waiting||message.offset()!=t.offset)return;
        if(t.loading||!t.prepared.isEmpty())return;
        t.lastTick=s.tick;t.waiting=false;Transfer current=t;t.loading=true;
        if(!s.submit(()->{
            try{var chunks=s.files.readWindow(current.source,current.offset);s.completions.add(()->{if(s.transfers.get(player.getUUID())==current){current.loading=false;current.prepared.addAll(chunks);}});}
            catch(Exception error){s.completions.add(()->{if(s.transfers.remove(player.getUUID(),current))PacketDistributor.sendToPlayer(player,new ServerAudioNetwork.Failure(current.source.track().sha256(),current.id));});}
        })){s.transfers.remove(player.getUUID(),current);PacketDistributor.sendToPlayer(player,new ServerAudioNetwork.Failure(message.hash(),message.transfer()));}
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        State s=state;if(s==null||s.server!=event.getServer())return;s.tick++;
        for(int i=0;i<128;i++){Runnable task=s.completions.poll();if(task==null)break;task.run();}
        if(s.tick-s.lastScan>=200)scan(s);
        s.transfers.entrySet().removeIf(e->s.server.getPlayerList().getPlayer(e.getKey())==null||s.tick-e.getValue().lastTick>200);
        List<UUID> peers=new ArrayList<>(s.transfers.keySet());if(peers.isEmpty())return;
        int sent=0,checked=0,start=Math.floorMod(s.rotation++,peers.size());
        while(sent<CHUNKS_PER_TICK&&checked<peers.size()*CHUNKS_PER_TICK){
            UUID id=peers.get((start+checked++)%peers.size());Transfer t=s.transfers.get(id);if(t==null||t.prepared.isEmpty())continue;
            ServerPlayer p=s.server.getPlayerList().getPlayer(id);if(p==null)continue;
            byte[] data=t.prepared.removeFirst();int offset=t.offset;t.offset+=data.length;t.lastTick=s.tick;
            boolean end=t.prepared.isEmpty();PacketDistributor.sendToPlayer(p,new ServerAudioNetwork.Chunk(t.source.track().sha256(),t.id,offset,data,end));sent++;
            if(end){if(t.offset==t.source.track().bytes())s.transfers.remove(id);else t.waiting=true;}
        }
    }
}
