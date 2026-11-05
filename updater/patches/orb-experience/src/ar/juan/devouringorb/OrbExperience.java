package ar.juan.devouringorb;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Collect green XP through the normal pickup path, including Clumps and Mending. */
public final class OrbExperience {
    private OrbExperience() {}

    public static void collect(ServerLevel level, ServerPlayer caster, Vec3 origin, AABB area, int stage) {
        if (!caster.isAlive() || caster.isSpectator() || caster.serverLevel() != level) return;
        // AABB intersection excludes an entity whose lower Y equals its maximum
        // Y. Include that boundary in the broad phase; the radial test stays exact.
        for (ExperienceOrb xp : level.getEntitiesOfClass(ExperienceOrb.class, area.inflate(1.0e-7),
                orb -> orb.isAlive() && OrbBalance.withinRadius(orb.position().distanceToSqr(origin), stage))) {
            // Clumps owns merged values, repair, pickup events and removal. Never
            // sum getValue(), grant XP directly, discard, or alter its merged map.
            xp.playerTouch(caster);
        }
    }
}
