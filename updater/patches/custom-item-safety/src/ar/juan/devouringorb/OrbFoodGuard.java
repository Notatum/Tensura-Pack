package ar.juan.devouringorb;

import ar.juan.devouringorb.mixin.ItemOwnershipAccessor;
import java.util.UUID;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Check ownership without serializing item NBT for every absorption candidate. */
public final class OrbFoodGuard {
    public static final String DEATH_OWNER = "devouring_orb_player_drop_owner";

    public static boolean isProtectedWeapon(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return id.equals("beelzebuth_bow:devouring_bow") || id.equals("devouring_orb:devouring_orb");
    }

    public static boolean mayConsume(ItemEntity drop, ServerPlayer caster) {
        if (isProtectedWeapon(drop.getItem())) return false;
        UUID player = caster.getUUID();
        ItemOwnershipAccessor ownership = (ItemOwnershipAccessor)drop;
        UUID reserved = ownership.devouringOrb$getTarget();
        UUID thrower = ownership.devouringOrb$getThrower();
        if (reserved != null && !reserved.equals(player) || thrower != null && !thrower.equals(player)) return false;
        return !drop.getPersistentData().hasUUID(DEATH_OWNER) || drop.getPersistentData().getUUID(DEATH_OWNER).equals(player);
    }

    public static boolean sameOwnership(ItemEntity first, ItemEntity second) {
        ItemOwnershipAccessor a = (ItemOwnershipAccessor)first, b = (ItemOwnershipAccessor)second;
        UUID firstDeath = first.getPersistentData().hasUUID(DEATH_OWNER) ? first.getPersistentData().getUUID(DEATH_OWNER) : null;
        UUID secondDeath = second.getPersistentData().hasUUID(DEATH_OWNER) ? second.getPersistentData().getUUID(DEATH_OWNER) : null;
        return Objects.equals(a.devouringOrb$getThrower(), b.devouringOrb$getThrower())
            && Objects.equals(firstDeath, secondDeath);
    }
}
