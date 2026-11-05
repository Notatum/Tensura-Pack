package ar.juan.devouringorb;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;

/** All methods perform disk work and must be called on the audio I/O executor. */
public final class ServerAudioFiles {
    public record Source(ServerTrack track,Path path,long modified,Object fileKey) {}
    public record Catalog(UUID serverId,List<Source> sources) {
        public Catalog { sources=List.copyOf(sources); }
        public List<ServerTrack> tracks() { return sources.stream().map(Source::track).toList(); }
        public Source find(String hash) { return sources.stream().filter(s->s.track().sha256().equals(hash)).findFirst().orElse(null); }
    }
    private final Path root,music;
    private final UUID serverId;
    private Catalog last;

    public ServerAudioFiles(Path serverDirectory) throws IOException {
        root=directory(serverDirectory,"orbe_audio_servidor");
        music=directory(root,"musica");
        Path identity=root.resolve("biblioteca-id.txt");
        if (!Files.exists(identity,LinkOption.NOFOLLOW_LINKS))
            Files.writeString(identity,UUID.randomUUID().toString(),StandardOpenOption.CREATE_NEW);
        if (Files.isSymbolicLink(identity) || !Files.isRegularFile(identity,LinkOption.NOFOLLOW_LINKS) || Files.size(identity)>64)
            throw new IOException("Invalid audio library identity file");
        try { serverId=UUID.fromString(Files.readString(identity).trim()); }
        catch (IllegalArgumentException invalid) { throw new IOException("Invalid audio library identity",invalid); }
        Path guide=root.resolve("LEEME.txt");
        if (!Files.exists(guide,LinkOption.NOFOLLOW_LINKS)) Files.writeString(guide,
                "BIBLIOTECA DE MUSICA DEL ORBE\n\nColoca canciones MP3, OGG Vorbis o WAV en musica/.\n"
                +"Limite: 64 MiB por archivo y 256 canciones. No se siguen enlaces simbolicos.\n"
                +"Dentro del juego: O > Parlante > Actualizar. Todos los jugadores pueden usar esta biblioteca.\n"
                +"Los archivos se descargan automaticamente por la conexion de Minecraft y se verifican con SHA-256.\n"
                +"No hacen falta puertos extra. Los clientes deben tener Orbe 1.4.0 o posterior compatible.\n"
                +"La primera escucha puede esperar a que termine la descarga.\n",StandardOpenOption.CREATE_NEW);
    }
    public Path musicDirectory() { return music; }
    public synchronized Catalog scan() throws IOException {
        List<Source> found=new ArrayList<>();
        Map<String,Source> previous=new HashMap<>();
        if (last!=null) last.sources().forEach(s->previous.put(s.track().name(),s));
        try (var paths=Files.list(music)) {
            for (Path path:paths.sorted(Comparator.comparing(p->p.getFileName().toString().toLowerCase(Locale.ROOT))).toList()) {
                String name=path.getFileName().toString();
                if (!AudioRules.allowedName(name) || Files.isSymbolicLink(path) || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) continue;
                var attrs=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if (attrs.size()<1 || attrs.size()>ServerTrack.MAX_BYTES) continue;
                if (found.size()>=ServerTrack.MAX_TRACKS) break;
                Source old=previous.get(name);
                if (old!=null && sameFile(old,attrs)) { found.add(old);continue; }
                String digest=sha256(path);
                var after=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if (!same(attrs,after)) continue; // A file still being copied is retried on the next scan.
                found.add(new Source(new ServerTrack(name,digest,(int)attrs.size()),path,attrs.lastModifiedTime().toMillis(),attrs.fileKey()));
            }
        }
        last=new Catalog(serverId,found);return last;
    }
    public List<byte[]> readWindow(Source source,int offset) throws IOException {
        if (offset<0 || offset>=source.track().bytes() || offset%ServerTrack.CHUNK_BYTES!=0) throw new IOException("Invalid audio offset");
        Path path=source.path();
        if (Files.isSymbolicLink(path) || !path.toRealPath().getParent().equals(music.toRealPath())) throw new IOException("Unsafe audio path");
        var before=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if (!sameFile(source,before)) throw new IOException("Audio file changed");
        List<byte[]> chunks=new ArrayList<>();
        try (FileChannel channel=FileChannel.open(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            channel.position(offset);
            for (int i=0;i<ServerTrack.WINDOW_CHUNKS && channel.position()<source.track().bytes();i++) {
                byte[] data=new byte[(int)Math.min(ServerTrack.CHUNK_BYTES,source.track().bytes()-channel.position())];
                ByteBuffer target=ByteBuffer.wrap(data);
                while(target.hasRemaining()) if(channel.read(target)<0) throw new IOException("Truncated audio");
                chunks.add(data);
            }
        }
        if (!sameFile(source,Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS))) throw new IOException("Audio file changed during transfer");
        return chunks;
    }
    private static boolean sameFile(Source s,BasicFileAttributes a) {
        return a.isRegularFile() && a.size()==s.track().bytes() && a.lastModifiedTime().toMillis()==s.modified() && Objects.equals(a.fileKey(),s.fileKey());
    }
    private static boolean same(BasicFileAttributes a,BasicFileAttributes b) {
        return a.size()==b.size() && a.lastModifiedTime().equals(b.lastModifiedTime()) && Objects.equals(a.fileKey(),b.fileKey());
    }
    public static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try (var input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)) { byte[] block=new byte[65536];int count;while((count=input.read(block))!=-1)digest.update(block,0,count); }
            return HexFormat.of().formatHex(digest.digest());
        } catch(java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    public static Path directory(Path root,String child) throws IOException {
        if (!child.matches("[a-zA-Z0-9_-]+")) throw new IOException("Invalid directory component");
        Path base=root.toAbsolutePath().normalize().toRealPath();Path next=base.resolve(child);
        if (Files.exists(next,LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(next) || !Files.isDirectory(next,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsafe audio directory");
        } else Files.createDirectory(next);
        if (!next.toRealPath().getParent().equals(base)) throw new IOException("Audio directory escapes root");
        return next;
    }
}
