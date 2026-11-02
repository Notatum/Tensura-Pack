package ar.juan.beelzebuthbow;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Both devouring weapons keep their stored progress when used together. */
public final class BowFoodGuard {
    public static boolean isProtectedWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return id.equals("beelzebuth_bow:devouring_bow") || id.equals("devouring_orb:devouring_orb");
    }
}
