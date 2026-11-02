package ar.juan.devouringorb;

import ar.juan.devouringorb.DevouringOrbItem;
import ar.juan.devouringorb.FeedingBatch;
import ar.juan.devouringorb.FoodValues;
import ar.juan.devouringorb.OrbAudioSettings;
import ar.juan.devouringorb.OrbBalance;
import ar.juan.devouringorb.OrbKills;
import ar.juan.devouringorb.OrbProgress;
import ar.juan.devouringorb.OrbVisuals;
import io.github.manasmods.tensura.entity.template.subclass.ILivingPartEntity;
import io.github.manasmods.tensura.util.SubordinateHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class OrbCombat {
    private OrbCombat() {
    }

    static void pulse(ServerLevel level, ServerPlayer caster, ItemStack orb) {
        int oldStage = OrbProgress.stage(orb);
        Vec3 origin = caster.position();
        AABB area = new AABB(origin, origin).inflate(OrbBalance.radius(oldStage));
        ArrayList<Vec3> foods = new ArrayList<Vec3>();
        FeedingBatch consumed = OrbCombat.consumeGround(level, caster, origin, area, oldStage, orb, foods);
        float damage = (float)Math.min(3.4028234663852886E38, OrbBalance.damage(OrbProgress.exactPoints(orb)));
        HashSet<UUID> seen = new HashSet<UUID>();
        int hits = 0;
        for (LivingEntity part : level.getEntitiesOfClass(LivingEntity.class, area)) {
            LivingEntity enemy = ILivingPartEntity.checkForHead((LivingEntity)part);
            if (!seen.add(enemy.getUUID()) || !OrbCombat.isEnemy(caster, enemy) || !OrbBalance.withinRadius(enemy.position().distanceToSqr(origin), oldStage) || !OrbKills.hurt(level, caster, orb, enemy, damage)) continue;
            ++hits;
        }
        consumed.merge(OrbCombat.consumeGround(level, caster, origin, area, oldStage, orb, foods));
        int stage = OrbProgress.stage(orb);
        DevouringOrbItem.applyCooldown((Player)caster, orb, stage);
        OrbVisuals.start(level, caster, origin, oldStage, OrbAudioSettings.read(orb), foods);
        caster.displayClientMessage((Component)Component.translatable((String)"message.devouring_orb.pulse", (Object[])new Object[]{hits, OrbProgress.format(consumed.count()), OrbProgress.format(consumed.total())}), true);
        if (stage > oldStage) {
            OrbCombat.evolved(caster, orb);
        }
    }

    private static boolean isEnemy(ServerPlayer caster, LivingEntity entity) {
        TamableAnimal tame;
        Mob mob;
        if (!(entity instanceof Mob) || !(mob = (Mob)entity).isAlive() || mob.isSpectator()) {
            return false;
        }
        if (mob.isAlliedTo((Entity)caster) || caster.isAlliedTo((Entity)mob) || SubordinateHelper.isAlly((LivingEntity)caster, (LivingEntity)mob)) {
            return false;
        }
        if (mob instanceof TamableAnimal && (tame = (TamableAnimal)mob).isTame()) {
            return false;
        }
        if (SubordinateHelper.getSubordinateOwnerUUID((LivingEntity)mob) != null) {
            return false;
        }
        String namespace = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace();
        return mob instanceof Enemy || mob.getTarget() == caster || namespace.equals("tensura") || namespace.equals("mysticism");
    }

    private static FeedingBatch consumeGround(ServerLevel level, ServerPlayer caster, Vec3 origin, AABB area, int stage, ItemStack orb, List<Vec3> foods) {
        FeedingBatch consumed = new FeedingBatch();
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, area, item -> item.isAlive() && !item.getItem().isEmpty() && OrbBalance.withinRadius(item.position().distanceToSqr(origin), stage))) {
            if (!OrbFoodGuard.mayConsume(drop, caster)) continue;
            ItemStack food = drop.getItem();
            String id = BuiltInRegistries.ITEM.getKey(food.getItem()).toString();
            if (!consumed.add(id, food.getCount(), FoodValues.value(food))) continue;
            if (foods.size() < 8) {
                foods.add(drop.position());
            }
            drop.setItem(ItemStack.EMPTY);
            drop.discard();
        }
        OrbProgress.feed(orb, consumed);
        return consumed;
    }

    static void evolved(ServerPlayer caster, ItemStack orb) {
        caster.displayClientMessage((Component)Component.translatable((String)"message.devouring_orb.evolved", (Object[])new Object[]{orb.getHoverName()}), false);
    }
}
