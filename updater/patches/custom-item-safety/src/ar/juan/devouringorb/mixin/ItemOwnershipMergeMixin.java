package ar.juan.devouringorb.mixin;

import ar.juan.devouringorb.OrbFoodGuard;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A merge must not erase the player identity used by orb absorption. */
@Mixin(ItemEntity.class)
public abstract class ItemOwnershipMergeMixin {
    @Inject(method="tryToMerge", at=@At("HEAD"), cancellable=true)
    private void devouringOrb$preserveOwner(ItemEntity other, CallbackInfo callback) {
        if (!OrbFoodGuard.sameOwnership((ItemEntity)(Object)this, other)) callback.cancel();
    }
}
