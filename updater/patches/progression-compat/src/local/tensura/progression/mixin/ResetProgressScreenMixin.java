package local.tensura.progression.mixin;

import io.github.manasmods.tensura.client.screen.MainScreen;
import io.github.manasmods.tensura.storage.ep.IExistence;
import local.tensura.progression.ResetAwakening;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MainScreen.class)
public abstract class ResetProgressScreenMixin {
    @Redirect(method="getResetCounterProgress", at=@At(value="INVOKE",
        target="Lio/github/manasmods/tensura/storage/ep/IExistence;isTrueHero()Z"), require=1)
    private boolean tensura$displayDivineFinalDragon(IExistence existence) {
        return existence.isTrueHero() || ResetAwakening.isDivineFinalDragon(Minecraft.getInstance().player);
    }
}
