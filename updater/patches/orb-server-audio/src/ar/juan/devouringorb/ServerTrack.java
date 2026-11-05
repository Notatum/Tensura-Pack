package ar.juan.devouringorb;

import java.util.Locale;
import java.util.regex.Pattern;

/** Display names are never interpreted as client-side paths. */
public record ServerTrack(String name,String sha256,int bytes) {
    public static final int MAX_BYTES=64*1024*1024;
    public static final int MAX_TRACKS=256;
    public static final int CHUNK_BYTES=32768;
    public static final int WINDOW_CHUNKS=4;
    public static final Pattern HASH=Pattern.compile("[0-9a-f]{64}");
    public ServerTrack {
        if (!AudioRules.allowedName(name) || !HASH.matcher(sha256).matches() || bytes<1 || bytes>MAX_BYTES)
            throw new IllegalArgumentException("Invalid server audio entry");
    }
    public String extension() { return name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT); }
}
