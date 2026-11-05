package ar.juan.devouringorb;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;

/** Content-addressed downloads never overwrite the user's local music. Disk work is asynchronous. */
public final class AudioCacheFiles {
    public static final long CACHE_BUDGET=512L*1024*1024;
    private final Path all,folder;
    private final List<Path> evicted=new ArrayList<>();
    public AudioCacheFiles(Path gameDirectory,UUID serverId) throws IOException {
        Path audio=ServerAudioFiles.directory(gameDirectory,"orbe_audio");
        all=ServerAudioFiles.directory(audio,"servidores");folder=ServerAudioFiles.directory(all,serverId.toString());
    }
    public Path path(ServerTrack track) { return folder.resolve(track.sha256()+track.extension()); }
    public List<Path> takeEvicted(){var result=List.copyOf(evicted);evicted.clear();return result;}
    public Path verified(ServerTrack track) throws IOException {
        Path target=path(track);
        if (!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) return null;
        if (Files.size(target)!=track.bytes() || !ServerAudioFiles.sha256(target).equals(track.sha256())) return null;
        Files.setLastModifiedTime(target,FileTime.fromMillis(System.currentTimeMillis()));return target;
    }
    public Path commit(ServerTrack track,byte[] bytes,Supplier<Set<Path>> protectedFiles) throws IOException {
        if (bytes.length!=track.bytes()) throw new IOException("Wrong audio length");
        try { if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(track.sha256())) throw new IOException("Audio SHA-256 mismatch"); }
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
        Path target=path(track),temporary=folder.resolve(track.sha256()+"-"+UUID.randomUUID()+".part");
        if (Files.isSymbolicLink(target)) throw new IOException("Unsafe cache target");
        try {
            Files.write(temporary,bytes,StandardOpenOption.CREATE_NEW);
            try { Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException unavailable) { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
        trim(target,protectedFiles);return target;
    }
    private void trim(Path newest,Supplier<Set<Path>> protectedFiles) throws IOException {
        List<Path> files=new ArrayList<>();
        try(var directories=Files.list(all)) {
            for(Path directory:directories.filter(p->!Files.isSymbolicLink(p)&&Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)).toList()) {
                try { UUID.fromString(directory.getFileName().toString()); }catch(IllegalArgumentException invalid){continue;}
                try(var entries=Files.list(directory)) { files.addAll(entries.filter(p->!Files.isSymbolicLink(p)&&Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&p.getFileName().toString().matches("[0-9a-f]{64}\\.(mp3|ogg|wav)")).toList()); }
            }
        }
        files.sort(Comparator.comparingLong(p->{try{return Files.getLastModifiedTime(p).toMillis();}catch(IOException e){return Long.MAX_VALUE;}}));
        long size=0;for(Path file:files)size+=Files.size(file);
        for(Path file:files) {
            if(size<=CACHE_BUDGET)break;
            if(file.equals(newest)||protectedFiles.get().contains(file))continue;
            long length=Files.size(file);try { if(Files.deleteIfExists(file)){size-=length;evicted.add(file);} }catch(IOException inUse){/* Windows can retain an open decoder handle. */}
        }
    }
    public static final class Incoming {
        private final ServerTrack track;private final byte[] bytes;private int offset;
        public Incoming(ServerTrack track){this.track=track;bytes=new byte[track.bytes()];}
        public int offset(){return offset;}
        public int percent(){return (int)(100L*offset/bytes.length);}
        public void accept(int position,byte[] data) throws IOException {
            if(position!=offset||data.length<1||data.length>ServerTrack.CHUNK_BYTES||position>bytes.length-data.length)throw new IOException("Invalid or out-of-order audio chunk");
            System.arraycopy(data,0,bytes,position,data.length);offset+=data.length;
        }
        public boolean complete(){return offset==bytes.length;}
        public byte[] bytes(){if(!complete())throw new IllegalStateException("Incomplete audio");return bytes;}
        public ServerTrack track(){return track;}
    }
}
