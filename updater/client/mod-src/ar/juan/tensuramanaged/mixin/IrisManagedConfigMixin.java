package ar.juan.tensuramanaged.mixin;

import ar.juan.tensuramanaged.ManagedPolicy;
import net.irisshaders.iris.config.IrisConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=IrisConfig.class, remap=false)
public abstract class IrisManagedConfigMixin {
    @Shadow private String shaderPackName;
    @Shadow private boolean enableShaders;

    @Inject(method="load",at=@At("RETURN"))
    private void tensura$loadPolicy(CallbackInfo callback) { enforce(); }

    @Inject(method="save",at=@At("HEAD"))
    private void tensura$savePolicy(CallbackInfo callback) { enforce(); }

    private void enforce() {
        if (ManagedPolicy.mandatory()) {
            shaderPackName=ManagedPolicy.shader();enableShaders=true;
        }
    }
    @ModifyVariable(method="setShaderPackName",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private String tensura$requiredPack(String requested) {
        return ManagedPolicy.mandatory()?ManagedPolicy.shader():requested;
    }
    @ModifyVariable(method="setShadersEnabled",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private boolean tensura$requiredEnabled(boolean requested) {
        return ManagedPolicy.mandatory()||requested;
    }
}
