package ar.juan.tensuramanaged.mixin;

import com.github.hvnbael.trnightmare.client.blackhole.compat.ShaderCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Initializes Nightmare's shader cache before its sentinel can overflow a delta. */
@Pseudo
@Mixin(targets="com.github.hvnbael.trnightmare.client.blackhole.compat.ShaderCompat", remap=false)
public abstract class NightmareShaderCacheMixin {
    @Shadow private static boolean cachedEnabled;
    @Shadow private static long cachedAt;

    @Inject(method="shadersEnabledCached()Z", at=@At("HEAD"), cancellable=true, remap=false)
    private static void tensura$initializeShaderCache(CallbackInfoReturnable<Boolean> callback) {
        if (cachedAt == Long.MIN_VALUE) {
            cachedEnabled = ShaderCompat.shadersEnabled();
            cachedAt = System.nanoTime();
            callback.setReturnValue(cachedEnabled);
        }
    }
}
