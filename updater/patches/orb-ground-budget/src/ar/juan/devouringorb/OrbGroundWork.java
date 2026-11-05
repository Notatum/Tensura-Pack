package ar.juan.devouringorb;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Snapshot only references; every actual pickup is revalidated on the server thread. */
final class OrbGroundWork {
    final ServerLevel level;
    final ServerPlayer caster;
    final Vec3 origin;
    final int stage;
    final List<ExperienceOrb> xp;
    final List<ItemEntity> items;
    int xpCursor, itemCursor;

    OrbGroundWork(ServerLevel level, ServerPlayer caster, Vec3 origin, AABB area, int stage) {
        this.level=level;this.caster=caster;this.origin=origin;this.stage=stage;
        xp=level.getEntitiesOfClass(ExperienceOrb.class,area.inflate(1.0e-7),entity->inRange(entity));
        items=level.getEntitiesOfClass(ItemEntity.class,area,entity->inRange(entity) && !entity.getItem().isEmpty()
                && OrbFoodGuard.mayConsume(entity,caster));
    }

    boolean hasNext() { return xpCursor<xp.size() || itemCursor<items.size(); }

    List<Vec3> preview() {
        List<Vec3> positions=new ArrayList<>();
        for (ItemEntity item:items) {
            if (positions.size()==8) break;
            if (inRange(item) && !item.getItem().isEmpty() && OrbFoodGuard.mayConsume(item,caster)) positions.add(item.position());
        }
        return positions;
    }

    private boolean inRange(net.minecraft.world.entity.Entity entity) {
        return entity.level()==level && entity.isAlive()
                && OrbBalance.withinRadius(entity.position().distanceToSqr(origin),stage);
    }

    void step(ItemStack orb, FeedingBatch consumed) {
        if (xpCursor<xp.size()) {
            ExperienceOrb entity=xp.get(xpCursor++);
            // Clumps retains its own merged map, events, repair and removal.
            if (inRange(entity)) entity.playerTouch(caster);
            return;
        }
        ItemEntity entity=items.get(itemCursor++);
        if (!inRange(entity) || entity.getItem().isEmpty() || !OrbFoodGuard.mayConsume(entity,caster)) return;
        ItemStack food=entity.getItem();
        FeedingBatch credit=new FeedingBatch();
        if (!credit.add(BuiltInRegistries.ITEM.getKey(food.getItem()).toString(),food.getCount(),FoodValues.value(food))) return;
        // Commit each consumed entity before removing it: cancellation cannot lose earlier credit.
        OrbProgress.feed(orb,credit);
        consumed.merge(credit);
        entity.setItem(ItemStack.EMPTY);
        entity.discard();
    }
}
