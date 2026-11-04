package ar.juan.tensuramanaged.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps unassigned key bindings inactive before they reach GLFW. */
@Mixin(value=InputConstants.class, remap=false)
public abstract class InputConstantsGuardMixin {
    @Inject(method="isKeyDown(JI)Z", at=@At("HEAD"), cancellable=true, remap=false)
    private static void tensura$ignoreUnknownKey(long window, int key,
            CallbackInfoReturnable<Boolean> callback) {
        if (key == GLFW.GLFW_KEY_UNKNOWN) {
            callback.setReturnValue(false);
        }
    }
}
