package ar.juan.devouringorb;

import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Caches registry-derived IDs only. Player requirements/results are never cached. */
@EventBusSubscriber(modid="devouring_orb")
public final class NightmareEvolutionCatalog {
    private record Cached(MinecraftServer server,List<String> ids) {}
    private static volatile Cached cached;
    private NightmareEvolutionCatalog() {}
    public static List<String> get() {
        Cached value=cached;
        MinecraftServer server=ServerLifecycleHooks.getCurrentServer();
        return value!=null && value.server()==server && server!=null ? value.ids() : null;
    }
    public static void store(List<String> ids) {
        MinecraftServer server=ServerLifecycleHooks.getCurrentServer();
        // Avoid caching a partially initialized or unavailable registry.
        if (server!=null && ids!=null && !ids.isEmpty()) cached=new Cached(server,List.copyOf(ids));
    }
    public static void clear() { cached=null; }
    @SubscribeEvent public static void starting(ServerAboutToStartEvent event) { clear(); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { clear(); }
    @SubscribeEvent public static void reload(AddReloadListenerEvent event) {
        event.addListener((ResourceManagerReloadListener)manager->clear());
    }
}
