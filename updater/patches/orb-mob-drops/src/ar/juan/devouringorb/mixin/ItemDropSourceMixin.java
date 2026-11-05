package ar.juan.devouringorb.mixin;
import ar.juan.devouringorb.OrbFoodGuard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemEntity.class)
public abstract class ItemDropSourceMixin {
    @Inject(method="setThrower",at=@At("TAIL"),require=1)
    private void devouringOrb$rememberSource(Entity source,CallbackInfo callback) {
        OrbFoodGuard.recordSource((ItemEntity)(Object)this,source);
    }
}
