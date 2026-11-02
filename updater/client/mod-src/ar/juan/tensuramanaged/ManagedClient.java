package ar.juan.tensuramanaged;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value="tensura_managed_client", dist=Dist.CLIENT)
public final class ManagedClient {
    public ManagedClient() {
        System.out.println("[TensuraManaged] Configuración de cliente administrada por el pack.");
    }
}
