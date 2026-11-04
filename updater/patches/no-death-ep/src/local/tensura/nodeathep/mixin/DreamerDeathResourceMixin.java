package local.tensura.nodeathep.mixin;

import io.github.Memoires.mysticism.ability.skill.unique.DreamerSkill;
import io.github.manasmods.tensura.storage.ep.IExistence;
import java.util.ArrayList;
import local.tensura.nodeathep.DeathEpPolicy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "io.github.Memoires.mysticism.ability.skill.unique.DreamerSkill", remap = false, priority = 2000)
public abstract class DreamerDeathResourceMixin {
    @Unique private static final ThreadLocal<Player> nodeatheploss$deathRestoreOwner = new ThreadLocal<>();

    @Shadow private void restoreStats(Player player, ArrayList<Object> stats) {
        throw new AssertionError("Mixin shadow");
    }

    @Redirect(method = "onDeath", at = @At(value = "INVOKE", target =
            "Lio/github/Memoires/mysticism/ability/skill/unique/DreamerSkill;restoreStats(Lnet/minecraft/world/entity/player/Player;Ljava/util/ArrayList;)V"),
            require = 1, expect = 1, allow = 1)
    private void nodeatheploss$scopedDeathRestore(DreamerSkill ignored, Player player, ArrayList<Object> stats) {
        if (!(player instanceof ServerPlayer)) {
            restoreStats(player, stats);
            return;
        }
        Player previous = nodeatheploss$deathRestoreOwner.get();
        nodeatheploss$deathRestoreOwner.set(player);
        try {
            restoreStats(player, stats);
        } finally {
            if (previous == null) {
                nodeatheploss$deathRestoreOwner.remove();
            } else {
                nodeatheploss$deathRestoreOwner.set(previous);
            }
        }
    }

    @Redirect(method = "restoreStats", at = @At(value = "INVOKE", target =
            "Lio/github/manasmods/tensura/storage/ep/IExistence;setMagicule(D)V"),
            require = 1, expect = 1, allow = 1)
    private void nodeatheploss$preserveDeathMagicule(IExistence storage, double requested,
            Player player, ArrayList<Object> stats) {
        if (nodeatheploss$deathRestoreOwner.get() != player
                || DeathEpPolicy.allowsDeathResourceWrite(player, storage.getMagicule(), requested)) {
            storage.setMagicule(requested);
        }
    }

    @Redirect(method = "restoreStats", at = @At(value = "INVOKE", target =
            "Lio/github/manasmods/tensura/storage/ep/IExistence;setAura(D)V"),
            require = 1, expect = 1, allow = 1)
    private void nodeatheploss$preserveDeathAura(IExistence storage, double requested,
            Player player, ArrayList<Object> stats) {
        if (nodeatheploss$deathRestoreOwner.get() != player
                || DeathEpPolicy.allowsDeathResourceWrite(player, storage.getAura(), requested)) {
            storage.setAura(requested);
        }
    }
}
