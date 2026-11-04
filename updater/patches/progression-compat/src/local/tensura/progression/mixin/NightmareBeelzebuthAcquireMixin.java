package local.tensura.progression.mixin;

import com.github.hvnbael.trnightmare.ability.skill.ultimate.BeelzebuthSkill;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BeelzebuthSkill.class)
public abstract class NightmareBeelzebuthAcquireMixin {
    @Inject(method="checkAcquiringRequirement", at=@At("HEAD"), cancellable=true, require=1)
    private void tensura$disableDuplicate(Player player, double ep, CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(false);
    }
}
