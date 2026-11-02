package com.github.hvnbael.trnightmare.feature.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/** Keep the custom orb's established scaling without bypassing boss phases or invulnerability. */
public final class OrbCapCompatibility {
    private static final ResourceKey<DamageType> PULSE=ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath("devouring_orb","abyssal_pulse"));
    public static DamageCapService.Limits limits(DamageCapService.Limits original,DamageSource source) {return source.is(PULSE)?null:original;}
    public static float cap(float original,DamageSource source) {return source.is(PULSE)?Float.POSITIVE_INFINITY:original;}
}
