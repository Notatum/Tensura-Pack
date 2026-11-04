package local.tensura.progression.mixin;

import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.tensura.menu.ReincarnationMenu;
import local.tensura.progression.AscensionTalents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReincarnationMenu.class)
public abstract class ReincarnationTalentsMixin {
    @Inject(method="setRace", at=@At("RETURN"), require=1)
    private static void tensura$refreshTalents(Player player, ManasRace race, boolean resetEP, boolean grantUnique, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) AscensionTalents.refresh(serverPlayer);
    }
}
