package com.juan.beelzebuth.compat;

import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Notify Great Sage after the batch, without replaying acquisition costs/gates. */
public final class SkillVoiceBridge {
    private static Method announce, sawSkills;
    private static boolean resolved, warned;
    private SkillVoiceBridge() {}
    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            announce = Class.forName("dev.dedek.greatsage.server.SkillAnnouncer")
                .getMethod("send", ServerPlayer.class, ManasSkill.class, boolean.class);
            sawSkills = Class.forName("dev.dedek.greatsage.server.SageProgress")
                .getMethod("sawSkills", ServerPlayer.class, List.class);
        } catch (ClassNotFoundException absent) {
            // Great Sage is optional outside the managed pack.
        } catch (ReflectiveOperationException incompatible) { warn(incompatible); }
    }
    public static void acquired(LivingEntity owner, List<ManasSkillInstance> acquired) {
        if (!(owner instanceof ServerPlayer player) || acquired.isEmpty()) return;
        resolve();
        if (sawSkills != null) {
            try { sawSkills.invoke(null, player, acquired.stream().map(s -> s.getSkillId().toString()).toList()); }
            catch (ReflectiveOperationException error) { warn(error); }
        }
        for (var instance : acquired) {
            if (announce != null) {
                try {
                    // An explicit predation action is not a login/racial-kit announcement.
                    announce.invoke(null, player, instance.getSkill(), false);
                    continue;
                } catch (ReflectiveOperationException error) { warn(error); }
            }
            player.sendSystemMessage(Component.translatable("manascore.skill.learn_skill", instance.getChatDisplayName(true)));
        }
    }
    private static void warn(Exception error) {
        if (warned) return;
        warned = true;
        System.err.println("[Beelzebuth] No se pudo integrar un aviso con la Voz del Mundo: " + error);
    }
}
