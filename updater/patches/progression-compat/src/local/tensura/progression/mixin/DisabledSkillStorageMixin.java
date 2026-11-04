package local.tensura.progression.mixin;

import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.impl.SkillStorage;
import local.tensura.progression.BeelzebuthProgression;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SkillStorage.class)
public abstract class DisabledSkillStorageMixin {
    @Shadow public abstract LivingEntity getOwner();
    @Inject(method="learnSkill", at=@At("HEAD"), cancellable=true, require=1)
    private void tensura$blockDuplicateLearn(ManasSkillInstance skill, MutableComponent message, CallbackInfoReturnable<Boolean> callback) {
        if (getOwner() instanceof Player && BeelzebuthProgression.DISABLED.equals(skill.getSkillId())) callback.setReturnValue(false);
    }
    @Inject(method="updateSkill", at=@At("HEAD"), cancellable=true, require=1)
    private void tensura$blockDuplicateCopy(ManasSkillInstance skill, boolean sync, CallbackInfo callback) {
        if (getOwner() instanceof Player && BeelzebuthProgression.DISABLED.equals(skill.getSkillId())) callback.cancel();
    }
}
