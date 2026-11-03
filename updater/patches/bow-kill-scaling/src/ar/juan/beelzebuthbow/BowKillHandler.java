package ar.juan.beelzebuthbow;

import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Credit the firing bow once, using the EP actually awarded by the confirmed kill. */
public final class BowKillHandler {
    private static final ThreadLocal<Pending> PENDING = new ThreadLocal<>();

    static boolean hurt(ServerLevel level, ServerPlayer shooter, ItemStack bow, LivingEntity victim, float damage) {
        Pending previous = PENDING.get();
        Pending current = new Pending(victim, shooter, bow);
        double epBefore = EnergyHelper.getBaseMaxEP(shooter);
        float healthBefore = victim.getHealth() + victim.getAbsorptionAmount();
        PENDING.set(current);
        try {
            DamageSource source = new DamageSource(level.registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BowDamage.BEAM), shooter);
            boolean hit = victim.hurt(source, damage);
            if (current.deathConfirmed && !victim.isAlive() && !(victim instanceof Player)) {
                double gained = EnergyHelper.getBaseMaxEP(shooter) - epBefore;
                if (Double.isFinite(gained) && gained > 0.0) credit(current, gained);
            }
            if (victim.isAlive()) {
                float actual = Math.max(0.0f, healthBefore - victim.getHealth() - victim.getAbsorptionAmount());
                shooter.displayClientMessage(actual > 0.0f
                        ? Component.translatable("message.beelzebuth_bow.hit", DevouringBowItem.format(actual))
                        : Component.translatable(hit ? "message.beelzebuth_bow.absorbed" : "message.beelzebuth_bow.blocked"), true);
            }
            return hit;
        } finally {
            if (previous == null) PENDING.remove();
            else PENDING.set(previous);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        Pending current = PENDING.get();
        if (current != null && !event.isCanceled() && current.victim == event.getEntity()
                && current.shooter == event.getSource().getEntity() && event.getSource().is(BowDamage.BEAM)) {
            current.deathConfirmed = true;
        }
    }

    private static void credit(Pending pending, double gained) {
        if (pending.credited) return;
        pending.credited = true;
        int previousStage = BowProgress.stage(pending.bow);
        BowProgress.add(pending.bow, gained);
        pending.shooter.displayClientMessage(Component.translatable("message.beelzebuth_bow.ep",
                DevouringBowItem.format(gained)), true);
        if (BowProgress.stage(pending.bow) > previousStage) {
            pending.shooter.displayClientMessage(Component.translatable("message.beelzebuth_bow.evolved",
                    pending.bow.getHoverName()), false);
        }
    }

    private static final class Pending {
        final LivingEntity victim;
        final ServerPlayer shooter;
        final ItemStack bow;
        boolean deathConfirmed;
        boolean credited;
        Pending(LivingEntity victim, ServerPlayer shooter, ItemStack bow) {
            this.victim = victim;
            this.shooter = shooter;
            this.bow = bow;
        }
    }
}
