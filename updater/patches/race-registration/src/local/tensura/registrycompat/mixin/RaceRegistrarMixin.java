package local.tensura.registrycompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.architectury.registry.registries.RegistrySupplier;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Addon constructors share this registrar while NeoForge constructs mods in parallel. */
@Mixin(targets = "dev.architectury.registry.registries.forge.RegistrarManagerImpl$RegistrarImpl", remap = false)
public class RaceRegistrarMixin {
    @Shadow @Final private Registry<?> delegate;
    @Shadow @Final private Map<?, ?> registry;

    @WrapMethod(method = "register", remap = false)
    private <E> RegistrySupplier<E> tensura$serializeRaceRegistration(
            ResourceLocation id, Supplier<E> factory, Operation<RegistrySupplier<E>> original) {
        ResourceLocation key = delegate.key().location();
        if (!key.getNamespace().equals("manascore_race") || !key.getPath().equals("races")) {
            return original.call(id, factory);
        }
        // The map belongs to the provider, so aliases of the registrar share the same lock.
        // Cover computeIfAbsent and LinkedHashMap.put together. The monitor also releases
        // on exceptions; constructors and the original RegistrySupplier remain untouched.
        synchronized (registry) {
            return original.call(id, factory);
        }
    }
}
