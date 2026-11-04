package local.tensura.nodeathep.mixin;

import com.simplygray.tensuraaddon.util.EpUtil;
import local.tensura.nodeathep.DeathEpPolicy;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.simplygray.tensuraaddon.handler.ModifierEffects", remap = false, priority = 2000)
public abstract class AscensionDeathPenaltyMixin {
    @Inject(method = "onDeath", at = @At("HEAD"), cancellable = true, require = 1)
    private static void nodeatheploss$skipPlayerDeathLoss(LivingDeathEvent event, CallbackInfo callback) {
        // The original player branch only creates loss and its compensating Echo.
        // Its non-player branch (OPEN_WOUNDS on the killer) is preserved.
        if (DeathEpPolicy.protects(event.getEntity())) {
            callback.cancel();
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target =
            "Lcom/simplygray/tensuraaddon/util/EpUtil;takeMaxEp(Lnet/minecraft/world/entity/LivingEntity;D)V"),
            require = 1, expect = 1, allow = 1)
    private static void nodeatheploss$skipDeferredDeathLoss(LivingEntity entity, double amount) {
        // The caller still removes the death-only `loss` tag immediately afterwards.
        if (!DeathEpPolicy.protects(entity)) {
            EpUtil.takeMaxEp(entity, amount);
        }
    }
}
