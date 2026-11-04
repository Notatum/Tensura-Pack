package local.tensura.progression;

import com.simplygray.tensuraaddon.codex.Talent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.extensions.IEntityExtension;

/** Preserve only the independent talent tree, never the character being reset. */
public final class AscensionTalents {
    private static final String PERSISTED = "PlayerPersisted";
    private static final String TALENTS = "ascension:talents";

    private AscensionTalents() {}

    public static CompoundTag snapshot(Player player) {
        CompoundTag root = ((IEntityExtension) player).getPersistentData().getCompound(PERSISTED);
        return root.contains(TALENTS, Tag.TAG_COMPOUND) ? root.getCompound(TALENTS).copy() : null;
    }

    public static void restore(Player player, CompoundTag saved) {
        if (saved == null) return;
        CompoundTag data = ((IEntityExtension) player).getPersistentData();
        if (!data.contains(PERSISTED, Tag.TAG_COMPOUND)) data.put(PERSISTED, new CompoundTag());
        data.getCompound(PERSISTED).put(TALENTS, saved.copy());
    }

    public static void refresh(ServerPlayer player) {
        // Ascension replaces modifiers by ID; calling this repeatedly cannot stack them.
        if (snapshot(player) == null) return;
        Talent.apply(player);
        if (player.connection != null) Talent.send(player);
    }
}
