package local.tensura.progression;

import com.juan.beelzebuth.BeelzebuthRegistry;
import com.juan.beelzebuth.compat.SkillVoiceBridge;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.SkillStorage;
import io.github.manasmods.tensura.util.EnergyHelper;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.extensions.IEntityExtension;

/** Server-authoritative unlock; the existing custom Beelzebuth JAR stays unchanged. */
public final class BeelzebuthProgression {
    public static final ResourceLocation DISABLED = ResourceLocation.parse("trnightmare:beelzebuth");
    public static final ResourceLocation CUSTOM = ResourceLocation.parse("beelzebuth:beelzebuth");
    private static final ResourceLocation PREDATOR = ResourceLocation.parse("tensura:predator");
    private static final ResourceLocation GLUTTONY = ResourceLocation.parse("tensura:gluttony");
    public static final double REQUIRED_EP = 2_000_000d;
    private BeelzebuthProgression() {}

    private static boolean learned(SkillStorage storage, ResourceLocation id) {
        return storage.getSkill(id).filter(skill -> skill.getMastery() >= 0 && !skill.isTemporarySkill()).isPresent();
    }

    public static boolean eligible(ServerPlayer player) {
        var storage = SkillAPI.getSkillsFrom(player);
        return player.isAlive() && EnergyHelper.getBaseMaxEP(player) >= REQUIRED_EP
            && learned(storage, PREDATOR) && learned(storage, GLUTTONY);
    }

    public static void apply(ServerPlayer player) {
        if (!player.isAlive()) return;
        var storage = SkillAPI.getSkillsFrom(player);
        var legacy = storage.getSkill(DISABLED).orElse(null);
        if (legacy != null) {
            // Keep the complete original instance, including private tags, for recovery.
            CompoundTag data = ((IEntityExtension) player).getPersistentData();
            CompoundTag persisted = data.getCompound("PlayerPersisted");
            CompoundTag backup = persisted.getCompound("tensura_progression_compat");
            if (!backup.contains("nightmare_beelzebuth_backup")) backup.put("nightmare_beelzebuth_backup", legacy.toNBT());
            persisted.put("tensura_progression_compat", backup);
            data.put("PlayerPersisted", persisted);
            var current = storage.getSkill(CUSTOM).orElse(null);
            if (current == null) {
                var replacement = BeelzebuthRegistry.get().createDefaultInstance();
                replacement.setMastery(legacy.getMastery());
                replacement.setRemoveTime(legacy.getRemoveTime());
                copyModes(legacy, replacement);
                if (!storage.learnSkill(replacement, null)) return;
                SkillVoiceBridge.acquired(player, List.of(replacement));
            } else {
                boolean becomingLearned = current.getMastery() < 0 && legacy.getMastery() >= 0;
                current.setMastery(Math.max(current.getMastery(), legacy.getMastery()));
                if (!legacy.isTemporarySkill()) current.setRemoveTime(-1);
                copyModes(legacy, current);
                if (becomingLearned) current.onLearnSkill(player);
                storage.markDirty();
            }
            // Forget only after the replacement has been installed successfully.
            storage.forgetSkill(DISABLED, null);
        }
        if (!eligible(player)) return;
        var current = storage.getSkill(CUSTOM).orElse(null);
        if (current == null) {
            var next = BeelzebuthRegistry.get().createDefaultInstance();
            next.setMastery(0);
            if (storage.learnSkill(next, null)) SkillVoiceBridge.acquired(player, List.of(next));
        } else if (current.getMastery() < 0 || current.isTemporarySkill()) {
            boolean becomingLearned = current.getMastery() < 0;
            current.setRemoveTime(-1);
            current.setMastery(Math.max(0, current.getMastery()));
            if (becomingLearned) current.onLearnSkill(player);
            storage.markDirty();
            SkillVoiceBridge.acquired(player, List.of(current));
        }
    }

    private static void copyModes(ManasSkillInstance source, ManasSkillInstance target) {
        if (source.getTag() == null) return;
        var skill = BeelzebuthRegistry.get();
        for (int mode = 0; mode < target.getModes(); mode++) {
            String key = skill.getModeId(target, mode);
            if (key != null && source.getTag().contains(key, 99))
                target.getOrCreateTag().putDouble(key, Math.max(target.getOrCreateTag().getDouble(key), source.getTag().getDouble(key)));
        }
    }
}
