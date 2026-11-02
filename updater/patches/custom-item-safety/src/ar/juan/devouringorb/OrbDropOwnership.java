package ar.juan.devouringorb;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** Protect player death drops from foreign orbs without changing normal pickup. */
@EventBusSubscriber(modid="devouring_orb")
public final class OrbDropOwnership {
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void playerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        for (ItemEntity drop : event.getDrops())
            drop.getPersistentData().putUUID(OrbFoodGuard.DEATH_OWNER, player.getUUID());
    }
}
