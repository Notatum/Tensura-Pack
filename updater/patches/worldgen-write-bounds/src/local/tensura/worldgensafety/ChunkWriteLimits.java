package local.tensura.worldgensafety;

/** The integer bounds used by WorldGenRegion, without logging or loading chunks. */
public final class ChunkWriteLimits {
    private ChunkWriteLimits() {}

    public static boolean contains(int centerX, int centerZ, int radius, int blockX, int blockZ) {
        long deltaX = Math.abs((long) centerX - (blockX >> 4));
        long deltaZ = Math.abs((long) centerZ - (blockZ >> 4));
        return deltaX <= radius && deltaZ <= radius;
    }

    public static boolean containsHeight(boolean upgrading, int minimum, int maximum, int y) {
        return !upgrading || (y >= minimum && y < maximum);
    }
}
