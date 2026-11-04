package local.tensura.progression.mixin;

import com.juan.beelzebuth.compat.TotalSkillTheft;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.Skills;
import java.util.Collection;
import local.tensura.progression.BeelzebuthProgression;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TotalSkillTheft.class)
public abstract class CustomBeelzebuthCopyMixin {
    @Redirect(method="steal", at=@At(value="INVOKE",target="Lio/github/manasmods/manascore/skill/api/Skills;getLearnedSkills()Ljava/util/Collection;"), require=1)
    private static Collection<ManasSkillInstance> tensura$omitDisabledDuplicate(Skills source, LivingEntity owner, LivingEntity target) {
        var skills=source.getLearnedSkills();
        if (!(owner instanceof Player)) return skills;
        return skills.stream().filter(skill -> !BeelzebuthProgression.DISABLED.equals(skill.getSkillId())).toList();
    }
}
