package local.tensura.progression.mixin;

import com.juan.beelzebuth.EvolutionHandler;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retire only the old grant check; its caller still maintains the persistent stomach. */
@Mixin(EvolutionHandler.class)
public abstract class CustomBeelzebuthEvolutionMixin {
    @Inject(method="tick", at=@At("HEAD"), cancellable=true, require=1)
    private static void tensura$useNewProgression(ServerPlayer player, CallbackInfo callback) {
        callback.cancel();
    }
}
