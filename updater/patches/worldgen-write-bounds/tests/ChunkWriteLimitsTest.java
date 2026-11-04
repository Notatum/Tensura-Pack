import local.tensura.worldgensafety.ChunkWriteLimits;

public final class ChunkWriteLimitsTest {
    public static void main(String[] args) {
        int checks = 0;
        for (int centerX : new int[] {-70, -2, -1, 0, 1, 32, 70}) {
            for (int centerZ : new int[] {-70, -2, -1, 0, 1, 32, 70}) {
                for (int radius : new int[] {-1, 0, 1, 2, 4}) {
                    for (int dx = -6; dx <= 6; dx++) {
                        for (int dz = -6; dz <= 6; dz++) {
                            for (int offset : new int[] {0, 1, 7, 15}) {
                                int x = (centerX + dx) * 16 + offset;
                                int z = (centerZ + dz) * 16 + (15 - offset);
                                boolean expected = radius >= 0 && Math.abs(dx) <= radius && Math.abs(dz) <= radius;
                                if (ChunkWriteLimits.contains(centerX, centerZ, radius, x, z) != expected) {
                                    throw new AssertionError("Mismatch at " + x + "," + z + " radius=" + radius);
                                }
                                checks++;
                            }
                        }
                    }
                }
            }
        }
        for (boolean upgrading : new boolean[] {false, true}) {
            for (int y : new int[] {-1000, -65, -64, -1, 0, 319, 320, 1000}) {
                boolean expected = !upgrading || (y >= -64 && y < 320);
                if (ChunkWriteLimits.containsHeight(upgrading, -64, 320, y) != expected) {
                    throw new AssertionError("Height mismatch at " + y);
                }
                checks++;
            }
        }
        System.out.println("CHUNK_WRITE_LIMITS checks=" + checks + " failures=0");
    }
}
