package local.tensura.nodeathep.mixin;

import io.github.hvnbael.legacytensura.capability.race.TensuraPlayerCapability;
import local.tensura.nodeathep.DeathEpPolicy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.github.hvnbael.trnightmare.ability.skill.unique.TimeTravelerHelper", remap = false, priority = 2000)
public abstract class TimeTravelerDeathPenaltyMixin {
    @Redirect(method = "applyMaxPenalty", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;setBaseValue(D)V"),
            require = 2, expect = 2, allow = 2)
    private static void nodeatheploss$preserveCurrentBase(AttributeInstance attribute, double requested,
            ServerPlayer player, CompoundTag tag) {
        DeathEpPolicy.writeSavepointBase(attribute, requested, player);
    }

    @Redirect(method = "applyMaxPenalty", at = @At(value = "INVOKE", target =
            "Lio/github/hvnbael/legacytensura/capability/race/TensuraPlayerCapability;setMagicule(Lnet/minecraft/world/entity/player/Player;D)V"),
            require = 1, expect = 1, allow = 1)
    private static void nodeatheploss$preserveDeathMagicule(Player player, double requested) {
        if (DeathEpPolicy.allowsDeathResourceWrite(player, TensuraPlayerCapability.getMagicule(player), requested)) {
            TensuraPlayerCapability.setMagicule(player, requested);
        }
    }

    @Redirect(method = "applyMaxPenalty", at = @At(value = "INVOKE", target =
            "Lio/github/hvnbael/legacytensura/capability/race/TensuraPlayerCapability;setAura(Lnet/minecraft/world/entity/player/Player;D)V"),
            require = 1, expect = 1, allow = 1)
    private static void nodeatheploss$preserveDeathAura(Player player, double requested) {
        if (DeathEpPolicy.allowsDeathResourceWrite(player, TensuraPlayerCapability.getAura(player), requested)) {
            TensuraPlayerCapability.setAura(player, requested);
        }
    }
}
