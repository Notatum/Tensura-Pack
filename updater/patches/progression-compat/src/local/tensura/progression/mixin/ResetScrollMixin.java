package local.tensura.progression.mixin;

import io.github.manasmods.tensura.item.misc.ResetScrollItem;
import io.github.manasmods.tensura.storage.ep.IExistence;
import local.tensura.progression.ResetAwakening;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ResetScrollItem.class)
public abstract class ResetScrollMixin {
    @Redirect(method="isFullReset", at=@At(value="INVOKE",
        target="Lio/github/manasmods/tensura/storage/ep/IExistence;isTrueHero()Z"), require=1)
    private static boolean tensura$acceptDivineFinalDragon(IExistence existence, ServerPlayer player) {
        return existence.isTrueHero() || ResetAwakening.isDivineFinalDragon(player);
    }
}
