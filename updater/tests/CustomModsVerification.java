package local.tensura.customreview;

import ar.juan.epdungeon.DungeonConfig;
import ar.juan.epdungeon.DungeonManager;
import ar.juan.epdungeon.RuntimeTuning120;
import ar.juan.epdungeon.TensuraBridge;
import com.mojang.authlib.GameProfile;
import io.github.manasmods.tensura.storage.TensuraStorages;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("tensura_custom_review")
public final class CustomModsVerification {
    private boolean started;
    private int passed;
    private int findings;
    public CustomModsVerification() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event)->{
            if(!started && Boolean.getBoolean("tensura.custom.review")) {
                started=true;review(event.getServer());
            }
        });
    }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static ItemStack stack(String value) { return BuiltInRegistries.ITEM.get(id(value)).getDefaultInstance(); }
    private void check(boolean value,String message) {
        if(!value) throw new IllegalStateException(message);
        passed++;System.out.println("[CUSTOM-REVIEW] PASS "+message);
    }
    private void finding(String message) { findings++;System.out.println("[CUSTOM-REVIEW] FINDING "+message); }
    private static Object call(String owner,String name,Class<?>[] types,Object...args) throws Exception {
        Method method=Class.forName(owner).getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(null,args);
    }
    private static void points(ItemStack stack,String key,String value,int version) {
        CustomData.update(DataComponents.CUSTOM_DATA,stack,root->{
            CompoundTag data=root.getCompound(key);data.putString("points",value);
            data.putInt("evolution_version",version);data.putInt("legacy_stage",0);root.put(key,data);
        });
    }
    private void review(MinecraftServer server) {
        try {
            ServerLevel level=server.overworld();
            var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"CustomReview"));
            ItemStack bow=stack("beelzebuth_bow:devouring_bow"),orb=stack("devouring_orb:devouring_orb");
            check(!bow.isEmpty()&&!orb.isEmpty(),"Both custom weapon items are registered");
            String bp="ar.juan.beelzebuthbow.BowProgress",op="ar.juan.devouringorb.OrbProgress";
            check(!bow.isDamageableItem()&&!orb.isDamageableItem(),"Both weapons are unbreakable");
            double[] bt={0,15000,105000,450000,1650000,4500000};
            for(int i=0;i<bt.length;i++) {
                points(bow,"BeelzebuthBow",BigDecimal.valueOf(bt[i]).toPlainString(),1);
                check((Integer)call(bp,"stage",new Class[]{ItemStack.class},bow)==i,"Bow evolution threshold "+i);
            }
            double[] ot={0,5000,15000,45000,105000,225000,450000,900000,1650000,4500000};
            for(int i=0;i<ot.length;i++) {
                points(orb,"DevouringOrb",BigDecimal.valueOf(ot[i]).toPlainString(),2);
                check((Integer)call(op,"stage",new Class[]{ItemStack.class},orb)==i,"Orb evolution threshold "+i);
            }
            points(orb,"DevouringOrb","123456.75",2);
            call(op,"ensureId",new Class[]{ItemStack.class},orb);
            ItemStack restored=ItemStack.parseOptional(server.registryAccess(),(CompoundTag)orb.save(server.registryAccess()));
            check(((BigDecimal)call(op,"exactPoints",new Class[]{ItemStack.class},restored)).compareTo(new BigDecimal("123456.75"))==0,"Orb fractional EP survives serialization");
            points(bow,"BeelzebuthBow","123456.75",1);
            ItemStack restoredBow=ItemStack.parseOptional(server.registryAccess(),(CompoundTag)bow.save(server.registryAccess()));
            check(((BigDecimal)call(bp,"exactPoints",new Class[]{ItemStack.class},restoredBow)).compareTo(new BigDecimal("123456.75"))==0,"Bow fractional EP survives serialization");
            System.out.println("[CUSTOM-REVIEW] DUNGEON CONFIG cap="+DungeonConfig.CAP.get()+" global="+DungeonConfig.GLOBAL_CAP.get()+" pauseMspt="+DungeonConfig.MAX_SPAWN_MSPT.get()+" armorWear="+DungeonConfig.ARMOR_WEAR_CAP.get());
            for(int tier=1;tier<=5;tier++) {
                Mob mob=(Mob)BuiltInRegistries.ENTITY_TYPE.get(id("tensura:orc")).create(level);
                mob.getPersistentData().putBoolean("epdungeon_mob",true);mob.getPersistentData().putInt("epdungeon_tier",tier);
                TensuraBridge.configure(mob,tier,0);RuntimeTuning120.applyDungeonMob(mob);
                double ep=TensuraBridge.maximumEP(mob),health=mob.getMaxHealth();
                RuntimeTuning120.applyDungeonMob(mob);
                check(TensuraBridge.maximumEP(mob)==ep && mob.getMaxHealth()==health,"Dungeon balancing is idempotent for floor "+tier);
                System.out.println("[CUSTOM-REVIEW] FLOOR "+tier+" EP="+ep+" HP="+health+" attackAttribute="+mob.getAttributeValue(Attributes.ATTACK_DAMAGE));
                check(!TensuraStorages.getExistenceFrom(mob).isSkippingEPDrop(),"Farming floor "+tier+" permits Tensura EP rewards");
            }
            for(String boss:List.of("mysticism:memoires","tensura:hinata_sakaguchi","tensura:gazel_dwargo","tensura:orc_disaster","tensura:ifrit","tensura:elemental_colossus","tensura:shizu"))
                check(BuiltInRegistries.ENTITY_TYPE.containsKey(id(boss)),"Quest boss is registered: "+boss);

            var other=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"OtherOwner"));
            var spawn=level.getSharedSpawnPos();Vec3 position=new Vec3(spawn.getX()+0.5,spawn.getY()+120,spawn.getZ()+0.5);
            level.getChunk(spawn);player.setPos(position.x,position.y,position.z);
            ItemStack freshOrb=stack("devouring_orb:devouring_orb");
            ItemEntity droppedOrb=new ItemEntity(level,position.x+1,position.y,position.z,orb.copy());
            ItemEntity droppedBow=new ItemEntity(level,position.x+2,position.y,position.z,bow.copy());
            check(level.addFreshEntity(droppedOrb)&&level.addFreshEntity(droppedBow),"Created weapon drop fixtures in the test world");
            CompoundTag orbBefore=(CompoundTag)droppedOrb.getItem().save(server.registryAccess());
            CompoundTag bowBefore=(CompoundTag)droppedBow.getItem().save(server.registryAccess());
            call("ar.juan.devouringorb.OrbCombat","pulse",new Class[]{ServerLevel.class,net.minecraft.server.level.ServerPlayer.class,ItemStack.class},level,player,freshOrb);
            check(!droppedOrb.isRemoved()&&!droppedBow.isRemoved(),"Orb cannot absorb either custom weapon from the ground");
            check(orbBefore.equals(droppedOrb.getItem().save(server.registryAccess()))&&bowBefore.equals(droppedBow.getItem().save(server.registryAccess())),"Protected ground weapons retain all NBT including EP and evolution");
            check(((BigDecimal)call(op,"exactPoints",new Class[]{ItemStack.class},freshOrb)).signum()==0,"Protected weapons grant no food EP");
            droppedOrb.discard();droppedBow.discard();
            for(boolean reverse:new boolean[]{false,true}) {
                var hand=reverse?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND;
                var foodHand=reverse?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;
                player.setItemInHand(hand,bow.copy());player.setItemInHand(foodHand,orb.copy());player.setShiftKeyDown(true);
                player.getItemInHand(hand).getItem().use(level,player,hand);
                check(!player.getItemInHand(foodHand).isEmpty(),"Bow preserves opposite-hand orb; reversed="+reverse);
                check(((BigDecimal)call(op,"exactPoints",new Class[]{ItemStack.class},player.getItemInHand(foodHand))).compareTo(new BigDecimal("123456.75"))==0,"Opposite-hand orb retains EP; reversed="+reverse);
            }
            player.setItemInHand(InteractionHand.MAIN_HAND,bow.copy());player.setItemInHand(InteractionHand.OFF_HAND,bow.copy());
            player.getMainHandItem().getItem().use(level,player,InteractionHand.MAIN_HAND);
            check(!player.getOffhandItem().isEmpty(),"Bow still protects another bow");
            player.setItemInHand(InteractionHand.OFF_HAND,stack("minecraft:apple"));
            player.getMainHandItem().getItem().use(level,player,InteractionHand.MAIN_HAND);
            check(player.getOffhandItem().isEmpty(),"Bow still consumes ordinary food");
            player.setShiftKeyDown(false);
            var drops=new ArrayList<ItemEntity>();
            for(int i=0;i<7;i++) drops.add(new ItemEntity(level,position.x+1,position.y,position.z,stack("minecraft:apple")));
            drops.get(1).setThrower(player);drops.get(2).setThrower(other);
            drops.get(3).setTarget(player.getUUID());drops.get(4).setTarget(other.getUUID());
            var foreignDeath=new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(other,level.damageSources().generic(),new ArrayList<>(List.of(drops.get(5))),false);
            NeoForge.EVENT_BUS.post(foreignDeath);
            var ownDeath=new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(player,level.damageSources().generic(),new ArrayList<>(List.of(drops.get(6))),false);
            NeoForge.EVENT_BUS.post(ownDeath);
            String ownerKey="devouring_orb_player_drop_owner";
            check(drops.get(5).getPersistentData().getUUID(ownerKey).equals(other.getUUID()),"Player death event records the true drop owner");
            check(!foreignDeath.isCanceled()&&!ownDeath.isCanceled(),"Player death drops remain enabled");
            CompoundTag deathTag=new CompoundTag();drops.get(5).saveWithoutId(deathTag);
            ItemEntity loadedDeath=new ItemEntity(level,position.x+1,position.y,position.z,stack("minecraft:apple"));loadedDeath.load(deathTag);drops.set(5,loadedDeath);
            check(loadedDeath.getPersistentData().getUUID(ownerKey).equals(other.getUUID()),"Death-drop ownership survives entity serialization");
            for(ItemEntity drop:drops) check(level.addFreshEntity(drop),"Created ownership absorption fixture");
            call("ar.juan.devouringorb.OrbCombat","pulse",new Class[]{ServerLevel.class,net.minecraft.server.level.ServerPlayer.class,ItemStack.class},level,player,freshOrb);
            for(int i:new int[]{0,1,3,6}) check(drops.get(i).isRemoved(),"Orb still consumes unowned or self-owned ordinary food: "+i);
            for(int i:new int[]{2,4,5}) check(!drops.get(i).isRemoved(),"Orb preserves another player's ordinary item: "+i);
            check(((BigDecimal)call(op,"exactPoints",new Class[]{ItemStack.class},freshOrb)).signum()>0,"Permitted food still grants EP");
            for(ItemEntity drop:drops) drop.discard();
            Method merge=ItemEntity.class.getDeclaredMethod("tryToMerge",ItemEntity.class);merge.setAccessible(true);
            ItemEntity one=new ItemEntity(level,0,0,0,stack("minecraft:apple")),two=new ItemEntity(level,0,0,0,stack("minecraft:apple"));
            one.setThrower(player);two.setThrower(other);merge.invoke(one,two);
            check(!one.isRemoved()&&!two.isRemoved()&&one.getItem().getCount()==1&&two.getItem().getCount()==1,"Foreign thrown items cannot merge away ownership");
            two.setThrower(player);merge.invoke(one,two);
            check(one.isRemoved()||two.isRemoved(),"Items thrown by the same player can still merge");
            one=new ItemEntity(level,0,0,0,stack("minecraft:apple"));two=new ItemEntity(level,0,0,0,stack("minecraft:apple"));
            one.getPersistentData().putUUID(ownerKey,player.getUUID());merge.invoke(one,two);
            check(!one.isRemoved()&&!two.isRemoved(),"Player death item cannot merge into an unowned item");
            two.getPersistentData().putUUID(ownerKey,player.getUUID());merge.invoke(one,two);
            check(one.isRemoved()||two.isRemoved(),"Same-owner death items can still merge");
            ItemEntity pickup=new ItemEntity(level,position.x,position.y,position.z,stack("minecraft:apple"));
            pickup.getPersistentData().putUUID(ownerKey,other.getUUID());pickup.setNoPickUpDelay();
            player.getInventory().clearContent();pickup.playerTouch(player);
            check(pickup.isRemoved(),"Orb death-drop protection does not prevent normal player pickup");
            ServerLevel dungeon=server.getLevel(DungeonManager.DIMENSION);
            check(dungeon!=null,"Dungeon dimension is loaded");
            int center=(Integer)call("ar.juan.epdungeon.ArenaBuilder","centerX",new Class[]{int.class},6);
            for(String item:List.of("beelzebuth_bow:devouring_bow","devouring_orb:devouring_orb","minecraft:apple")) {
                ItemEntity floor6Drop=new ItemEntity(dungeon,center+0.5,64,0.5,stack(item));
                check(dungeon.addFreshEntity(floor6Drop)&&!floor6Drop.isRemoved(),"Floor 6 preserves dropped item: "+item);floor6Drop.discard();
            }
            Mob boss=(Mob)BuiltInRegistries.ENTITY_TYPE.get(id("tensura:orc")).create(dungeon);
            var mobDrops=new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(boss,dungeon.damageSources().generic(),new ArrayList<>(),false);
            NeoForge.EVENT_BUS.post(mobDrops);check(mobDrops.isCanceled(),"Dungeon still blocks mob death loot");
            var dungeonPlayer=FakePlayerFactory.get(dungeon,new GameProfile(UUID.randomUUID(),"DungeonDrop"));
            var playerDrops=new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(dungeonPlayer,dungeon.damageSources().generic(),new ArrayList<>(),false);
            NeoForge.EVENT_BUS.post(playerDrops);check(!playerDrops.isCanceled(),"Dungeon does not block player death belongings");
            check(DungeonConfig.CAP.get()==20&&DungeonConfig.GLOBAL_CAP.get()==100&&DungeonConfig.MAX_SPAWN_MSPT.get()==0.0,"Current population and performance keys load correctly");
            System.out.println("[CUSTOM-REVIEW] RESULT passed="+passed+" findings="+findings+" errors=0");
        } catch(Throwable error) {
            error.printStackTrace();System.out.println("[CUSTOM-REVIEW] RESULT errors=1");
        } finally { server.execute(()->server.halt(false)); }
    }
}
