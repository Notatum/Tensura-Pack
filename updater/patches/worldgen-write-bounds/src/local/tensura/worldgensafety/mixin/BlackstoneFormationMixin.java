package local.tensura.worldgensafety.mixin;

import alexthw.ars_elemental.world.BlackstoneFormation;
import local.tensura.worldgensafety.WorldGenWriteBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = BlackstoneFormation.class, remap = false)
public abstract class BlackstoneFormationMixin {
    @Shadow private static boolean isTooFar(BlockPos origin, BlockPos position) {
        throw new AssertionError("Mixin shadow");
    }

    @Redirect(method = "place", at = @At(value = "INVOKE", target =
            "Lalexthw/ars_elemental/world/BlackstoneFormation;isTooFar(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Z"),
            require = 2, expect = 2, allow = 2)
    private boolean tensuraWorldgenSafety$withinWritableRegion(BlockPos origin, BlockPos position,
            FeaturePlaceContext<NoneFeatureConfiguration> context) {
        return isTooFar(origin, position) || !WorldGenWriteBounds.canWriteHorizontally(context.level(), position);
    }

    @Redirect(method = "place", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/LevelAccessor;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"),
            require = 6, expect = 6, allow = 6)
    private boolean tensuraWorldgenSafety$writeAllowedBlock(LevelAccessor level, BlockPos position,
            BlockState state, int flags) {
        return WorldGenWriteBounds.canWrite(level, position) && level.setBlock(position, state, flags);
    }
}
