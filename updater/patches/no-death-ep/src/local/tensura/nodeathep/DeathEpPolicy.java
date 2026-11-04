package local.tensura.nodeathep;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/** Called only at the audited death-penalty sites, never as a global EP floor. */
public final class DeathEpPolicy {
    private DeathEpPolicy() {}

    public static boolean protects(LivingEntity entity) {
        return entity instanceof ServerPlayer;
    }

    public static void writeSavepointBase(AttributeInstance attribute, double requested, LivingEntity entity) {
        if (!protects(entity) || requested >= attribute.getBaseValue()) {
            attribute.setBaseValue(requested);
        }
    }

    public static boolean allowsDeathResourceWrite(LivingEntity entity, double current, double requested) {
        // Skip equal combined-EP writes too: setEP would repartition AP/MP in half.
        return !protects(entity) || requested > current;
    }
}
