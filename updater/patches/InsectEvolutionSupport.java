package local.tensura.patch;

import io.github.manasmods.manascore.race.api.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import java.util.ArrayList;
import java.util.List;

/** Completes Origins' configured insect path without using reincarnation/reset APIs. */
public final class InsectEvolutionSupport {
    private InsectEvolutionSupport() {}
    public static List<ManasRace> next(ManasRaceInstance current, LivingEntity entity) {
        List<ManasRace> result=new ArrayList<>();
        ManasRace target=RaceAPI.getRaceRegistry().get(ResourceLocation.parse("trorigins:insect_dragon"));
        if(target!=null && target.getPreviousEvolutions(target.createDefaultInstance(),entity).contains(current.getRace())) {
            result.add(target);
        }
        return result;
    }
}
