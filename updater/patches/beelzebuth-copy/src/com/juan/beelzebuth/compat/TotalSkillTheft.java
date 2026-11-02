package com.juan.beelzebuth.compat;

import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.TensuraSkill;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/** Copy a complete target snapshot in this tick; repeated calls are idempotent. */
public final class TotalSkillTheft {
    private TotalSkillTheft() {}
    public static void steal(LivingEntity owner, LivingEntity target) {
        if (owner == null || target == null || owner == target || !(owner.level() instanceof ServerLevel)) return;
        var source = SkillCompat.getSkillsFrom(target);
        var destination = SkillCompat.getSkillsFrom(owner);
        if (source == null || destination == null || source == destination) return;
        var changed = new LinkedHashMap<ResourceLocation, ManasSkillInstance>();
        var acquired = new ArrayList<ManasSkillInstance>();
        for (var original : List.copyOf(source.getLearnedSkills())) {
            if (original == null || original.getSkillId() == null) continue;
            if (original.getMastery() < 0 && !SkillCopyPolicy.INCLUDE_LEARNING) continue;
            var id = original.getSkillId();
            try {
                double mastery = Math.max(0, original.getMastery());
                var current = destination.getSkill(id);
                if (current.isEmpty()) {
                    // ManasSkillInstance.copy() loses Tensura's concrete instance type,
                    // and clones active state, cooldowns, owners and stored inventories.
                    var copy = original.getSkill().createDefaultInstance();
                    copy.setMastery(mastery);
                    copy.setRemoveTime(original.getRemoveTime());
                    copyModeProgress(original, copy);
                    destination.updateSkill(copy, false);
                    changed.put(id, copy);
                    acquired.add(copy);
                } else {
                    var copy = current.get();
                    boolean wasLearning = copy.getMastery() < 0;
                    boolean modesChanged = copyModeProgress(original, copy);
                    if (mastery > copy.getMastery()) {
                        copy.setMastery(mastery);
                        destination.updateSkill(copy, false);
                        changed.put(id, copy);
                        if (wasLearning) {
                            copy.setCoolDowns(0);
                            acquired.add(copy);
                        }
                    } else if (modesChanged) {
                        destination.updateSkill(copy, false);
                        changed.put(id, copy);
                    }
                }
            } catch (RuntimeException | LinkageError error) { report(id, "preparar", error); }
        }
        // Install every skill before invoking hooks. A faulty hook cannot abort
        // the batch or suppress the synchronization of all the remaining skills.
        for (var copy : acquired) {
            try { copy.onLearnSkill(owner); }
            catch (RuntimeException | LinkageError error) { report(copy.getSkillId(), "inicializar", error); }
        }
        for (var entry : changed.entrySet()) {
            if (destination.getSkill(entry.getKey()).orElse(null) != entry.getValue())
                destination.updateSkill(entry.getValue(), false);
        }
        if (!changed.isEmpty()) destination.markDirty();
        SkillVoiceBridge.acquired(owner, acquired);
    }
    private static boolean copyModeProgress(ManasSkillInstance source, ManasSkillInstance copy) {
        if (source.getTag() == null || !(source.getSkill() instanceof TensuraSkill skill)) return false;
        boolean changed = false;
        // Keep unlocked mode progress, while private storage/target bindings stay
        // with their original player. Never overwrite a recipient's own stomach.
        for (int mode = 0; mode < source.getModes(); mode++) {
            String key = skill.getModeId(source, mode);
            if (key != null && source.getTag().contains(key, 99)) {
                double progress = source.getTag().getDouble(key);
                if (progress > copy.getOrCreateTag().getDouble(key)) {
                    copy.getOrCreateTag().putDouble(key, progress); changed = true;
                }
            }
        }
        return changed;
    }
    private static void report(ResourceLocation id, String action, Throwable error) {
        System.err.println("[Beelzebuth] No se pudo " + action + " la habilidad " + id + ": " + error);
    }
}
