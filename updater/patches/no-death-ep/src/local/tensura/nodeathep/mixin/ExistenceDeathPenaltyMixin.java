package local.tensura.nodeathep.mixin;

import io.github.manasmods.tensura.storage.ep.ExistenceStorage;
import io.github.manasmods.tensura.storage.ep.IExistence;
import local.tensura.nodeathep.DeathEpPolicy;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ExistenceStorage.class, remap = false, priority = 2000)
public abstract class ExistenceDeathPenaltyMixin {
    @Inject(method = "applyDeathPenalty", at = @At("HEAD"), cancellable = true, require = 1)
    private static void nodeatheploss$protectPlayer(LivingEntity entity, IExistence storage,
            DamageSource source, CallbackInfo callback) {
        if (DeathEpPolicy.protects(entity)) {
            callback.cancel();
        }
    }
}
