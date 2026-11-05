package ar.juan.devouringorb;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** No references to client classes: safe to load on the dedicated server. */
@EventBusSubscriber(modid="devouring_orb",bus=EventBusSubscriber.Bus.MOD)
public final class ServerAudioNetwork {
    public static Consumer<Catalog> clientCatalog=m->{};
    public static Consumer<Chunk> clientChunk=m->{};
    public static Consumer<Failure> clientFailure=m->{};
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("devouring_orb",name));
    }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var registry=event.registrar("5");
        registry.playToServer(Query.TYPE,Query.CODEC,(m,c)->{if(c.player() instanceof ServerPlayer p)ServerAudioServer.query(p);});
        registry.playToServer(Request.TYPE,Request.CODEC,(m,c)->{if(c.player() instanceof ServerPlayer p)ServerAudioServer.request(p,m);});
        registry.playToClient(Catalog.TYPE,Catalog.CODEC,(m,c)->clientCatalog.accept(m));
        registry.playToClient(Chunk.TYPE,Chunk.CODEC,(m,c)->clientChunk.accept(m));
        registry.playToClient(Failure.TYPE,Failure.CODEC,(m,c)->clientFailure.accept(m));
    }
    public record Query() implements CustomPacketPayload {
        public static final Type<Query> TYPE=ServerAudioNetwork.type("audio_query");
        public static final StreamCodec<RegistryFriendlyByteBuf,Query> CODEC=StreamCodec.of((b,m)->{},b->new Query());
        public Type<Query> type(){return TYPE;}
    }
    public record Catalog(boolean enabled,UUID server,List<ServerTrack> tracks) implements CustomPacketPayload {
        public Catalog { tracks=List.copyOf(tracks);if(tracks.size()>ServerTrack.MAX_TRACKS)throw new IllegalArgumentException("Audio catalog too large"); }
        public static final Type<Catalog> TYPE=ServerAudioNetwork.type("audio_catalog");
        public static final StreamCodec<RegistryFriendlyByteBuf,Catalog> CODEC=StreamCodec.of((b,m)->{
            b.writeBoolean(m.enabled);b.writeUUID(m.server);b.writeVarInt(m.tracks.size());
            for(var t:m.tracks){b.writeUtf(t.name(),255);b.writeUtf(t.sha256(),64);b.writeVarInt(t.bytes());}
        },b->{
            boolean enabled=b.readBoolean();UUID id=b.readUUID();int count=b.readVarInt();
            if(count<0||count>ServerTrack.MAX_TRACKS)throw new IllegalArgumentException("Invalid audio count");
            List<ServerTrack> tracks=new ArrayList<>();Set<String> names=new HashSet<>();
            for(int i=0;i<count;i++){var t=new ServerTrack(b.readUtf(255),b.readUtf(64),b.readVarInt());if(!names.add(t.name()))throw new IllegalArgumentException("Duplicate audio name");tracks.add(t);}
            return new Catalog(enabled,id,tracks);
        });
        public Type<Catalog> type(){return TYPE;}
    }
    public record Request(String hash,long transfer,int offset) implements CustomPacketPayload {
        public Request {if(!ServerTrack.HASH.matcher(hash).matches()||offset< -1||offset>ServerTrack.MAX_BYTES)throw new IllegalArgumentException("Invalid audio request");}
        public static final Type<Request> TYPE=ServerAudioNetwork.type("audio_request");
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,m)->{b.writeUtf(m.hash,64);b.writeLong(m.transfer);b.writeVarInt(m.offset);},b->new Request(b.readUtf(64),b.readLong(),b.readVarInt()));
        public Type<Request> type(){return TYPE;}
    }
    public record Chunk(String hash,long transfer,int offset,byte[] data,boolean windowEnd) implements CustomPacketPayload {
        public Chunk {if(!ServerTrack.HASH.matcher(hash).matches()||offset<0||offset>ServerTrack.MAX_BYTES||data.length<1||data.length>ServerTrack.CHUNK_BYTES)throw new IllegalArgumentException("Invalid audio chunk");}
        public static final Type<Chunk> TYPE=ServerAudioNetwork.type("audio_chunk");
        public static final StreamCodec<RegistryFriendlyByteBuf,Chunk> CODEC=StreamCodec.of((b,m)->{b.writeUtf(m.hash,64);b.writeLong(m.transfer);b.writeVarInt(m.offset);b.writeByteArray(m.data);b.writeBoolean(m.windowEnd);},b->new Chunk(b.readUtf(64),b.readLong(),b.readVarInt(),b.readByteArray(ServerTrack.CHUNK_BYTES),b.readBoolean()));
        public Type<Chunk> type(){return TYPE;}
    }
    public record Failure(String hash,long transfer) implements CustomPacketPayload {
        public static final Type<Failure> TYPE=ServerAudioNetwork.type("audio_failure");
        public static final StreamCodec<RegistryFriendlyByteBuf,Failure> CODEC=StreamCodec.of((b,m)->{b.writeUtf(m.hash,64);b.writeLong(m.transfer);},b->new Failure(b.readUtf(64),b.readLong()));
        public Type<Failure> type(){return TYPE;}
    }
}
