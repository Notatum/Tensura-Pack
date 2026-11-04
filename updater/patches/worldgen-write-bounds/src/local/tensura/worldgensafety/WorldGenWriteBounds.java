package local.tensura.worldgensafety;

import local.tensura.worldgensafety.mixin.WorldGenRegionAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;

public final class WorldGenWriteBounds {
    private WorldGenWriteBounds() {}

    public static boolean canWrite(LevelAccessor level, BlockPos position) {
        if (!(level instanceof WorldGenRegion region)) {
            return true;
        }
        if (!canWriteHorizontally(level, position)) {
            return false;
        }
        WorldGenRegionAccessor access = (WorldGenRegionAccessor) region;
        ChunkAccess center = access.tensuraWorldgenSafety$getCenter();
        if (center.isUpgrading()) {
            LevelHeightAccessor height = center.getHeightAccessorForGeneration();
            return ChunkWriteLimits.containsHeight(true, height.getMinBuildHeight(),
                    height.getMaxBuildHeight(), position.getY());
        }
        return true;
    }

    /** Early loop guards may test a position one block above the actual write. */
    public static boolean canWriteHorizontally(LevelAccessor level, BlockPos position) {
        if (!(level instanceof WorldGenRegion region)) {
            return true;
        }
        WorldGenRegionAccessor access = (WorldGenRegionAccessor) region;
        ChunkPos centerPosition = region.getCenter();
        int radius = access.tensuraWorldgenSafety$getGeneratingStep().blockStateWriteRadius();
        return ChunkWriteLimits.contains(centerPosition.x, centerPosition.z, radius,
                position.getX(), position.getZ());
    }
}
