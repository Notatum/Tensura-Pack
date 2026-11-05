package ar.juan.devouringorb;

import io.github.manasmods.tensura.entity.template.subclass.ILivingPartEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Damage stays on the server thread, with a shared budget across every caster. */
@EventBusSubscriber(modid="devouring_orb")
public final class OrbPulseWork {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final int MAX_HITS_PER_TICK = 8;
    static final long BUDGET_NANOS = 6_000_000L;
    private static final int MAX_QUEUED_PER_PLAYER = 2;
    private static final ArrayDeque<Job> PENDING = new ArrayDeque<>();
    private static final Map<UUID, Integer> COUNTS = new HashMap<>();

    private OrbPulseWork() {}

    static void start(ServerLevel level, ServerPlayer caster, ItemStack orb) {
        if (COUNTS.getOrDefault(caster.getUUID(), 0) >= MAX_QUEUED_PER_PLAYER) {
            caster.displayClientMessage(Component.translatable("message.devouring_orb.pulse_busy"), true);
            return;
        }
        int stage = OrbProgress.stage(orb);
        Vec3 origin = caster.position();
        AABB area = new AABB(origin, origin).inflate(OrbBalance.radius(stage));
        ArrayList<Vec3> foods = new ArrayList<>();
        FeedingBatch consumed = OrbCombat.consumeGround(level, caster, origin, area, stage, orb, foods);
        float damage = (float) Math.min(Float.MAX_VALUE, OrbDamage.damage(orb));
        HashSet<UUID> seen = new HashSet<>();
        ArrayList<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity part : level.getEntitiesOfClass(LivingEntity.class, area)) {
            LivingEntity enemy = ILivingPartEntity.checkForHead(part);
            if (seen.add(enemy.getUUID()) && OrbCombat.isEnemy(caster, enemy)
                    && OrbBalance.withinRadius(enemy.position().distanceToSqr(origin), stage)) targets.add(enemy);
        }
        int cooldownStage = OrbProgress.stage(orb);
        DevouringOrbItem.applyCooldown(caster, orb, cooldownStage);
        // A cast responds immediately; sound and the wave are emitted just once.
        OrbVisuals.start(level, caster, origin, stage, OrbAudioSettings.read(orb), foods);
        Job job = new Job(level, caster, orb, origin, area, stage, cooldownStage, damage, targets, consumed, foods);
        if (targets.isEmpty()) {
            job.finish();
        } else {
            OrbProgress.ensureId(orb);
            job.orbId = OrbProgress.id(orb);
            PENDING.addLast(job);
            COUNTS.merge(caster.getUUID(), 1, Integer::sum);
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void tick(ServerTickEvent.Post event) {
        long started = System.nanoTime();
        int actions = 0;
        while (!PENDING.isEmpty() && actions < MAX_HITS_PER_TICK
                && System.nanoTime() - started < BUDGET_NANOS) {
            Job job = PENDING.removeFirst();
            if (!job.valid(event.getServer())) {
                removed(job);
                continue;
            }
            try {
                if (job.cursor >= job.targets.size()) {
                    job.finish();
                    removed(job);
                    actions++;
                } else {
                    LivingEntity target = job.targets.get(job.cursor++);
                    if (target.level() == job.level && target.isAlive()
                            && OrbCombat.isEnemy(job.caster, target)
                            && OrbBalance.withinRadius(target.position().distanceToSqr(job.origin), job.stage)) {
                        if (OrbKills.hurt(job.level, job.caster, job.orb, target, job.damage)) job.hits++;
                        actions++;
                    }
                    PENDING.addLast(job);
                }
            } catch (RuntimeException failure) {
                removed(job);
                LOGGER.error("Failed processing queued Devouring Orb pulse; remaining targets were left untouched", failure);
                job.caster.displayClientMessage(Component.translatable("message.devouring_orb.pulse_interrupted"), true);
            }
        }
    }

    private static void removed(Job job) {
        COUNTS.computeIfPresent(job.caster.getUUID(), (key, count) -> count <= 1 ? null : count - 1);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        PENDING.removeIf(job -> job.caster.getUUID().equals(id));
        COUNTS.remove(id);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        PENDING.clear();
        COUNTS.clear();
    }

    private static final class Job {
        final ServerLevel level;
        final ServerPlayer caster;
        ItemStack orb;
        UUID orbId;
        final Vec3 origin;
        final AABB area;
        final int stage, cooldownStage;
        final float damage;
        final List<LivingEntity> targets;
        final FeedingBatch consumed;
        final List<Vec3> foods;
        int cursor, hits;

        Job(ServerLevel level, ServerPlayer caster, ItemStack orb, Vec3 origin, AABB area, int stage,
                int cooldownStage, float damage, List<LivingEntity> targets, FeedingBatch consumed, List<Vec3> foods) {
            this.level=level;this.caster=caster;this.orb=orb;this.origin=origin;this.area=area;
            this.stage=stage;this.cooldownStage=cooldownStage;this.damage=damage;this.targets=targets;
            this.consumed=consumed;this.foods=foods;
        }

        boolean valid(MinecraftServer server) {
            if (level.getServer()!=server || !caster.isAlive() || caster.isRemoved()
                    || caster.isSpectator() || caster.serverLevel()!=level || caster.hasDisconnected()) return false;
            for (int slot=0;slot<caster.getInventory().getContainerSize();slot++) {
                ItemStack item = caster.getInventory().getItem(slot);
                if (item.isEmpty()) continue;
                if (item==orb) return true;
                if (item.is(orb.getItem()) && orbId.equals(OrbProgress.id(item))) {
                    orb=item;
                    return true;
                }
            }
            return false;
        }

        void finish() {
            consumed.merge(OrbCombat.consumeGround(level, caster, origin, area, stage, orb, foods));
            int current = OrbProgress.stage(orb);
            if (current>cooldownStage && caster.getCooldowns().isOnCooldown(orb.getItem()))
                DevouringOrbItem.applyCooldown(caster, orb, current);
            caster.displayClientMessage(Component.translatable("message.devouring_orb.pulse",
                    hits, OrbProgress.format(consumed.count()), OrbProgress.format(consumed.total())), true);
            if (current>stage) OrbCombat.evolved(caster, orb);
        }
    }
}
