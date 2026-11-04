package local.tensura.worldgensafety.mixin;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = WorldGenRegion.class, remap = false)
public interface WorldGenRegionAccessor {
    @Accessor("generatingStep") ChunkStep tensuraWorldgenSafety$getGeneratingStep();
    @Accessor("center") ChunkAccess tensuraWorldgenSafety$getCenter();
}
