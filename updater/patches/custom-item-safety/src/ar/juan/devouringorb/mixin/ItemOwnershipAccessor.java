package ar.juan.devouringorb.mixin;

import java.util.UUID;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemEntity.class)
public interface ItemOwnershipAccessor {
    @Accessor("thrower") UUID devouringOrb$getThrower();
    @Accessor("target") UUID devouringOrb$getTarget();
}
