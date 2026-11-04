package local.tensura.nodeathep.mixin;

import io.github.hvnbael.legacytensura.capability.race.ITensuraPlayerCapability;
import local.tensura.nodeathep.DeathEpPolicy;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.github.hvnbael.trnightmare.ability.skill.laguna.PhoenixNextSkill", remap = false, priority = 2000)
public abstract class PhoenixNextDeathResourceMixin {
    @Redirect(method = "lambda$onDeath$1", at = @At(value = "INVOKE", target =
            "Lio/github/hvnbael/legacytensura/capability/race/ITensuraPlayerCapability;setMagicule(D)V"),
            require = 1, expect = 1, allow = 1)
    private static void nodeatheploss$preserveDeathMagicule(ITensuraPlayerCapability storage, double requested,
            Player player, ITensuraPlayerCapability originalStorage) {
        if (DeathEpPolicy.allowsDeathResourceWrite(player, storage.getMagicule(), requested)) {
            storage.setMagicule(requested);
        }
    }

    @Redirect(method = "lambda$onDeath$1", at = @At(value = "INVOKE", target =
            "Lio/github/hvnbael/legacytensura/capability/race/ITensuraPlayerCapability;setAura(D)V"),
            require = 1, expect = 1, allow = 1)
    private static void nodeatheploss$preserveDeathAura(ITensuraPlayerCapability storage, double requested,
            Player player, ITensuraPlayerCapability originalStorage) {
        if (DeathEpPolicy.allowsDeathResourceWrite(player, storage.getAura(), requested)) {
            storage.setAura(requested);
        }
    }
}
