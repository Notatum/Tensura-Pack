package ar.juan.devouringorb;

import ar.juan.devouringorb.mixin.ItemOwnershipAccessor;
import ar.juan.devouringorb.mixin.ItemSourceAccessor;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IEntityExtension;

/** Distinguish proven non-player sources without releasing unknown/player ownership. */
public final class OrbFoodGuard {
    public static final String DEATH_OWNER="devouring_orb_player_drop_owner";
    public static final String NON_PLAYER_SOURCE="devouring_orb_non_player_drop_source";
    private static CompoundTag data(ItemEntity drop) { return ((IEntityExtension)drop).getPersistentData(); }
    public static boolean isProtectedWeapon(ItemStack stack) {
        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return id.equals("beelzebuth_bow:devouring_bow") || id.equals("devouring_orb:devouring_orb");
    }
    public static void recordSource(ItemEntity drop,Entity source) {
        if (source==null || source instanceof Player) data(drop).remove(NON_PLAYER_SOURCE);
        else data(drop).putUUID(NON_PLAYER_SOURCE,source.getUUID());
    }
    public static boolean mayConsume(ItemEntity drop,ServerPlayer caster) {
        if (isProtectedWeapon(drop.getItem())) return false;
        UUID player=caster.getUUID();
        ItemOwnershipAccessor ownership=(ItemOwnershipAccessor)drop;
        UUID reserved=ownership.devouringOrb$getTarget(),thrower=ownership.devouringOrb$getThrower();
        CompoundTag tag=data(drop);
        if (reserved!=null && !reserved.equals(player)) return false;
        if (tag.hasUUID(DEATH_OWNER) && !tag.getUUID(DEATH_OWNER).equals(player)) return false;
        if (thrower==null || thrower.equals(player)) return true;
        if (tag.hasUUID(NON_PLAYER_SOURCE) && thrower.equals(tag.getUUID(NON_PLAYER_SOURCE))) return true;
        // Old in-memory drops may still retain their original, even removed, source.
        Entity source=((ItemSourceAccessor)drop).devouringOrb$getCachedThrower();
        if (source==null) source=drop.getOwner();
        if (source!=null && !(source instanceof Player) && thrower.equals(source.getUUID())) {
            recordSource(drop,source);return true;
        }
        // A UUID alone does not establish that an old/offline source was a mob.
        return false;
    }
    public static boolean sameOwnership(ItemEntity first,ItemEntity second) {
        ItemOwnershipAccessor a=(ItemOwnershipAccessor)first,b=(ItemOwnershipAccessor)second;
        return Objects.equals(a.devouringOrb$getThrower(),b.devouringOrb$getThrower())
                && Objects.equals(owner(first,DEATH_OWNER),owner(second,DEATH_OWNER))
                && Objects.equals(owner(first,NON_PLAYER_SOURCE),owner(second,NON_PLAYER_SOURCE));
    }
    private static UUID owner(ItemEntity drop,String key) { return data(drop).hasUUID(key)?data(drop).getUUID(key):null; }
}
