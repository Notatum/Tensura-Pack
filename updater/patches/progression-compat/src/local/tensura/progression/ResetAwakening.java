package local.tensura.progression;

import io.github.manasmods.manascore.race.api.RaceAPI;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;

/** Compatibility applies only to reset-counter eligibility, never to awakening flags. */
public final class ResetAwakening {
    private static final Set<String> DIVINE_DRAGONS = Set.of(
        "trnightmare:divine_dragon_lord", "trnightmare:divine_gehenna_dragon",
        "trnightmare:divine_heavenly_dragon", "trnightmare:divine_netherite_dragon",
        "trnightmare:divine_vampiric_dragon_lord", "trnightmare:divine_void_dragon"
    );

    private ResetAwakening() {}

    public static boolean isDivineFinalDragon(LivingEntity player) {
        if (player == null) return false;
        return RaceAPI.getRaceFrom(player).getRace()
            .filter(race -> DIVINE_DRAGONS.contains(race.getRaceId().toString()))
            .filter(race -> race.getNextEvolutions(player).isEmpty()).isPresent();
    }
}
