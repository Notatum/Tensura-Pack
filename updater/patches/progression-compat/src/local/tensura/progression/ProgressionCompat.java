package local.tensura.progression;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;

@Mod("tensura_progression_compat")
public final class ProgressionCompat {
    public ProgressionCompat() {
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post event) -> {
            if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0)
                BeelzebuthProgression.apply(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) BeelzebuthProgression.apply(player);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (PlayerEvent.Clone event) -> {
            AscensionTalents.restore(event.getEntity(), AscensionTalents.snapshot(event.getOriginal()));
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) AscensionTalents.refresh(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) AscensionTalents.refresh(player);
        });
    }
}
