package ar.juan.devouringorb.mixin;

import ar.juan.devouringorb.NightmareEvolutionCatalog;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.github.hvnbael.trnightmare.command.SkillEvolutionCommands",remap=false)
public abstract class NightmareEvolutionCatalogMixin {
    @Inject(method="evolutionChecklistSuggestions()Ljava/util/List;",at=@At("HEAD"),cancellable=true,require=1)
    private static void devouringOrb$reuseCatalog(CallbackInfoReturnable<List<String>> callback) {
        List<String> cached=NightmareEvolutionCatalog.get();
        if (cached!=null) callback.setReturnValue(new ArrayList<>(cached));
    }
    @Inject(method="evolutionChecklistSuggestions()Ljava/util/List;",at=@At("RETURN"),require=1)
    private static void devouringOrb$rememberCatalog(CallbackInfoReturnable<List<String>> callback) {
        if (NightmareEvolutionCatalog.get()==null) NightmareEvolutionCatalog.store(callback.getReturnValue());
    }
}
