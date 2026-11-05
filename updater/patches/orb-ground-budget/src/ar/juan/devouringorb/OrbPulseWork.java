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

/** Ground pickups and damage share one bounded server-thread queue. */
@EventBusSubscriber(modid="devouring_orb")
public final class OrbPulseWork {
    private static final Logger LOGGER=LogUtils.getLogger();
    static final int MAX_HITS_PER_TICK=8;
    static final int MAX_ACTIONS_PER_TICK=32;
    static final long BUDGET_NANOS=6_000_000L;
    private static final int MAX_QUEUED_PER_PLAYER=2;
    private static final ArrayDeque<Job> PENDING=new ArrayDeque<>();
    private static final Map<UUID,Integer> COUNTS=new HashMap<>();
    private OrbPulseWork() {}

    static void start(ServerLevel level,ServerPlayer caster,ItemStack orb) {
        if (COUNTS.getOrDefault(caster.getUUID(),0)>=MAX_QUEUED_PER_PLAYER) {
            caster.displayClientMessage(Component.translatable("message.devouring_orb.pulse_busy"),true);
            return;
        }
        OrbProgress.ensureId(orb);
        int stage=OrbProgress.stage(orb);
        Vec3 origin=caster.position();
        AABB area=new AABB(origin,origin).inflate(OrbBalance.radius(stage));
        OrbGroundWork ground=new OrbGroundWork(level,caster,origin,area,stage);
        HashSet<UUID> seen=new HashSet<>();
        ArrayList<LivingEntity> targets=new ArrayList<>();
        for (LivingEntity part:level.getEntitiesOfClass(LivingEntity.class,area)) {
            LivingEntity enemy=ILivingPartEntity.checkForHead(part);
            if (seen.add(enemy.getUUID()) && OrbCombat.isEnemy(caster,enemy)
                    && OrbBalance.withinRadius(enemy.position().distanceToSqr(origin),stage)) targets.add(enemy);
        }
        DevouringOrbItem.applyCooldown(caster,orb,stage);
        OrbVisuals.start(level,caster,origin,stage,OrbAudioSettings.read(orb),ground.preview());
        Job job=new Job(level,caster,orb,origin,area,stage,targets,ground);
        if (!ground.hasNext()) job.prepareDamage();
        if (targets.isEmpty() && !ground.hasNext()) {
            job.finish();
        } else {
            PENDING.addLast(job);
            COUNTS.merge(caster.getUUID(),1,Integer::sum);
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void tick(ServerTickEvent.Post event) {
        long started=System.nanoTime();
        int actions=0,hits=0,blocked=0;
        while (!PENDING.isEmpty() && actions<MAX_ACTIONS_PER_TICK
                && System.nanoTime()-started<BUDGET_NANOS) {
            Job job=PENDING.removeFirst();
            try {
                if (!job.valid(event.getServer())) { removed(job);continue; }
                if (job.needsHit() && hits>=MAX_HITS_PER_TICK) {
                    PENDING.addLast(job);
                    if (++blocked>=PENDING.size()) break;
                    continue;
                }
                blocked=0;
                if (job.needsHit()) hits++;
                actions++;
                if (job.step()) removed(job);else PENDING.addLast(job);
            } catch (RuntimeException failure) {
                removed(job);
                LOGGER.error("Failed processing queued Devouring Orb pulse; remaining targets and pickups were left untouched",failure);
                job.caster.displayClientMessage(Component.translatable("message.devouring_orb.pulse_interrupted"),true);
            }
        }
    }

    private static void removed(Job job) {
        COUNTS.computeIfPresent(job.caster.getUUID(),(key,count)->count<=1?null:count-1);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id=event.getEntity().getUUID();
        PENDING.removeIf(job->job.caster.getUUID().equals(id));COUNTS.remove(id);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { PENDING.clear();COUNTS.clear(); }

    private static final class Job {
        final ServerLevel level;
        final ServerPlayer caster;
        ItemStack orb;
        final UUID orbId;
        final Vec3 origin;
        final AABB area;
        final int stage;
        final List<LivingEntity> targets;
        final FeedingBatch consumed=new FeedingBatch();
        ItemStack damageSnapshot;
        OrbGroundWork ground;
        float damage;
        int cursor,hits,phase,cooldownStage;

        Job(ServerLevel level,ServerPlayer caster,ItemStack orb,Vec3 origin,AABB area,int stage,
                List<LivingEntity> targets,OrbGroundWork ground) {
            this.level=level;this.caster=caster;this.orb=orb;this.origin=origin;this.area=area;
            this.stage=stage;this.cooldownStage=stage;this.targets=targets;this.ground=ground;
            this.orbId=OrbProgress.id(orb);this.damageSnapshot=orb.copy();
        }

        boolean valid(MinecraftServer server) {
            if (level.getServer()!=server || !caster.isAlive() || caster.isRemoved()
                    || caster.isSpectator() || caster.serverLevel()!=level || caster.hasDisconnected()) return false;
            for (int slot=0;slot<caster.getInventory().getContainerSize();slot++) {
                ItemStack item=caster.getInventory().getItem(slot);
                if (item.isEmpty()) continue;
                if (item==orb) return true;
                if (item.is(orb.getItem()) && orbId.equals(OrbProgress.id(item))) { orb=item;return true; }
            }
            return false;
        }

        boolean needsHit() { return phase==1 && cursor<targets.size(); }

        void prepareDamage() {
            // Preserve damage at cast time plus this pulse's own pre-combat food.
            // Other queued kills or later EP changes cannot amplify this snapshot.
            OrbProgress.feed(damageSnapshot,consumed);
            damage=(float)Math.min(Float.MAX_VALUE,OrbDamage.damage(damageSnapshot));
            damageSnapshot=null;
            adjustCooldown();phase=1;
        }

        boolean step() {
            if (phase==0) {
                if (ground.hasNext()) ground.step(orb,consumed);else prepareDamage();
            } else if (phase==1) {
                if (cursor<targets.size()) {
                    LivingEntity target=targets.get(cursor++);
                    if (target.level()==level && target.isAlive() && OrbCombat.isEnemy(caster,target)
                            && OrbBalance.withinRadius(target.position().distanceToSqr(origin),stage)
                            && OrbKills.hurt(level,caster,orb,target,damage)) hits++;
                } else {
                    ground=new OrbGroundWork(level,caster,origin,area,stage);phase=2;
                }
            } else {
                if (ground.hasNext()) ground.step(orb,consumed);
                else { finish();return true; }
            }
            return false;
        }

        void adjustCooldown() {
            int current=OrbProgress.stage(orb);
            if (current>cooldownStage && caster.getCooldowns().isOnCooldown(orb.getItem()))
                DevouringOrbItem.applyCooldown(caster,orb,current);
            cooldownStage=current;
        }
        void finish() {
            adjustCooldown();
            caster.displayClientMessage(Component.translatable("message.devouring_orb.pulse",hits,
                    OrbProgress.format(consumed.count()),OrbProgress.format(consumed.total())),true);
            if (OrbProgress.stage(orb)>stage) OrbCombat.evolved(caster,orb);
        }
    }
}
