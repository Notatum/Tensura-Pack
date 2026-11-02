package local.tensura.beelzebuthverify;

import com.juan.beelzebuth.compat.TotalSkillTheft;
import com.mojang.authlib.GameProfile;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.dedek.greatsage.network.SkillAnnouncePayload;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.SkillRegistry;
import io.github.manasmods.tensura.ability.TensuraSkill;
import io.github.manasmods.tensura.ability.skill.Skill;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("beelzebuth_copy_verification")
public final class BeelzebuthCopyVerification {
    private static final RegistrySupplier<FaultySkill> FAULTY=SkillRegistry.SKILLS.register(id("beelzebuth_copy_verification:faulty"),FaultySkill::new);
    private boolean started;
    private int passed,findings;
    private final boolean fixed=Boolean.getBoolean("beelzebuth.verify.fixed");
    public BeelzebuthCopyVerification(){
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e)->{
            if(!started && Boolean.getBoolean("beelzebuth.verify")){started=true;run(e.getServer());}
        });
    }
    private static ResourceLocation id(String value){return ResourceLocation.parse(value);}
    private void check(boolean condition,String message){
        if(condition){passed++;System.out.println("[BEEL-VERIFY] PASS "+message);}
        else{findings++;System.out.println("[BEEL-VERIFY] FINDING "+message);if(fixed)throw new IllegalStateException(message);}
    }
    private static ServerPlayer player(ServerLevel level,String name){
        var result=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),name));
        result.tickCount=200;
        result.connection=new RecordingListener(level.getServer(),result);
        return result;
    }
    private static ManasSkillInstance add(ServerPlayer player,ManasSkill skill,double mastery){
        var instance=skill.createDefaultInstance();instance.setMastery(mastery);SkillAPI.getSkillsFrom(player).updateSkill(instance,false);return instance;
    }
    private static RecordingListener recording(ServerPlayer player){return (RecordingListener)player.connection;}
    private static ManasSkill skill(String value){return SkillAPI.getSkillRegistry().get(id(value));}
    private static ManasSkillInstance get(ServerPlayer player,ManasSkill skill){return SkillAPI.getSkillsFrom(player).getSkill(skill).orElseThrow();}
    private void run(MinecraftServer server){
        try{
            ServerLevel level=server.overworld();
            var owner=player(level,"CopyOwner");var other=player(level,"OtherOwner");var target=player(level,"CopyTarget");
            List<ManasSkill> skills=new ArrayList<>();
            boolean includeLearning=Boolean.getBoolean("beelzebuth.verify.learning");
            Map<Skill.SkillType,ManasSkill> categories=new EnumMap<>(Skill.SkillType.class);
            for(var candidate:SkillAPI.getSkillRegistry()){
                if(!(candidate instanceof Skill s) || !s.getRegistryName().getNamespace().equals("tensura"))continue;
                if(s.getType()==Skill.SkillType.RESISTANCE)skills.add(s);
                else if(s.getType()!=Skill.SkillType.UNIQUE && s.getType()!=Skill.SkillType.ULTIMATE)categories.putIfAbsent(s.getType(),s);
            }
            skills.addAll(categories.values());
            skills.add(skill("tensura:great_sage"));skills.add(skill("beelzebuth:beelzebuth"));
            check(skills.size()>15,"Fixture includes many resistances plus intrinsic/common/extra/unique/ultimate skills");
            var latent=SkillAPI.getSkillRegistry().get(id("tensura:predator"));
            var negative=add(target,latent,-25);
            ManasSkill existing=skills.get(0);
            var ownExisting=add(owner,existing,999999);ownExisting.getOrCreateTag().putString("PrivateOwnerData","preserve");
            var pendingSkill=skill("tensura:great_sage");add(owner,pendingSkill,-50);
            Map<ResourceLocation,net.minecraft.nbt.CompoundTag> sourceBefore=new HashMap<>();
            for(var entry:skills){
                var instance=add(target,entry,321);instance.setCoolDowns(80);instance.setToggled(true);
                instance.getOrCreateTag().putString("PrivateTargetData","must not clone");
                if(entry instanceof TensuraSkill s && instance.getModes()>0)instance.getOrCreateTag().putDouble(s.getModeId(instance,0),123);
                sourceBefore.put(entry.getRegistryName(),instance.toNBT());
            }
            long tick=server.getTickCount();TotalSkillTheft.steal(owner,target);
            check(server.getTickCount()==tick,"Entire transfer completes synchronously in one server tick");
            var acquired=SkillAPI.getSkillsFrom(owner);
            check(skills.stream().allMatch(s->acquired.getSkill(s).isPresent()),"Every learned target skill is copied in the first batch");
            check(skills.stream().allMatch(s->get(owner,s).getClass()==s.createDefaultInstance().getClass()),"Copied skills retain their concrete instance type");
            check(skills.stream().filter(s->s!=existing).allMatch(s->get(owner,s).getMastery()==321),"All target mastery values are transferred");
            check(skills.stream().filter(s->s!=existing).allMatch(s->!get(owner,s).onCoolDown(0)),"New skills do not inherit the target cooldowns");
            check(skills.stream().filter(s->s!=existing).allMatch(s->!get(owner,s).isToggled()),"New skills do not inherit active toggles");
            check(skills.stream().allMatch(s->get(owner,s).getTag()==null || !get(owner,s).getTag().contains("PrivateTargetData")),"Private inventories/owner data do not cross the skill copy");
            check(get(owner,existing).getMastery()==999999 && get(owner,existing).getTag().getString("PrivateOwnerData").equals("preserve"),"Stronger mastery and private recipient data survive");
            check(get(owner,pendingSkill).getMastery()==321,"An existing learning skill becomes usable immediately");
            check(skills.stream().allMatch(s->get(target,s).toNBT().equals(sourceBefore.get(s.getRegistryName()))),"Target skill data remains byte-for-byte unchanged");
            check(recording(owner).announcements.size()==skills.size()-1+(includeLearning?1:0),"Every newly acquired skill emits its Great Sage Voice payload exactly once");
            int announcements=recording(owner).announcements.size();TotalSkillTheft.steal(owner,target);
            check(recording(owner).announcements.size()==announcements,"Repeating predation does not duplicate acquisition notices");
            TotalSkillTheft.steal(other,target);
            check(skills.stream().allMatch(s->SkillAPI.getSkillsFrom(other).getSkill(s).isPresent()),"A second player can copy the same target immediately");
            var newSkill=skill("tensura:gluttony");add(target,newSkill,456);TotalSkillTheft.steal(owner,target);
            check(acquired.getSkill(newSkill).map(s->s.getMastery()==456).orElse(false),"A newly acquired target skill is copied without a five-minute delay");
            var modeSkill=skills.stream().filter(s->s instanceof TensuraSkill && s.getModes(get(target,s))>0).findFirst().orElseThrow();
            var modeSource=get(target,modeSkill);var modeKey=((TensuraSkill)modeSkill).getModeId(modeSource,0);
            modeSource.getOrCreateTag().putDouble(modeKey,567);TotalSkillTheft.steal(owner,target);
            check(get(owner,modeSkill).getOrCreateTag().getDouble(modeKey)>=567,"Mode progress updates even when mastery is unchanged");
            check(includeLearning?acquired.getSkill(latent).map(s->s.getMastery()==0).orElse(false):acquired.getSkill(latent).isEmpty(),"Unlearned target skills follow the configured copy policy");
            // New explicit acquisitions should be announced even just after login.
            var recent=player(level,"RecentLogin");recent.tickCount=1;TotalSkillTheft.steal(recent,target);
            check(recording(recent).announcements.size()==skills.size()+1+(includeLearning?1:0),"Explicit predation notices are not suppressed by login grace");
            var faultTarget=player(level,"FaultTarget");var faultOwner=player(level,"FaultOwner");
            add(faultTarget,FAULTY.get(),5);for(var s:skills)add(faultTarget,s,22);
            try{TotalSkillTheft.steal(faultOwner,faultTarget);}catch(RuntimeException ignored){}
            check(skills.stream().allMatch(s->SkillAPI.getSkillsFrom(faultOwner).getSkill(s).isPresent()),"A broken initializer cannot interrupt the remaining skill batch");
            check(recording(faultOwner).announcements.size()==skills.size(),"Voice notices still arrive when another skill initializer fails");
            var saved=new CompoundTag();SkillAPI.getSkillsFrom(owner).save(saved);
            var loadedOwner=player(level,"LoadedOwner");SkillAPI.getSkillsFrom(loadedOwner).load(saved);
            check(skills.stream().allMatch(s->SkillAPI.getSkillsFrom(loadedOwner).getSkill(s).isPresent()),"Copied skills survive a storage save/load round trip");
            check(skills.stream().allMatch(s->get(loadedOwner,s).getClass()==s.createDefaultInstance().getClass()),"Saved skill instances retain their concrete type after reload");
            check(get(loadedOwner,existing).getMastery()==999999,"Save/load preserves the recipient stronger mastery");
            var temporaryTarget=player(level,"TemporaryTarget");var temporaryOwner=player(level,"TemporaryOwner");
            var temporary=add(temporaryTarget,existing,15);temporary.setRemoveTime(30);
            TotalSkillTheft.steal(temporaryOwner,temporaryTarget);
            check(get(temporaryOwner,existing).getRemoveTime()==30,"Copying a temporary skill retains its original lifetime");
            var coneOwner=player(level,"ConeOwner");
            coneOwner.setPos(10000,120,10000);coneOwner.setYRot(0);coneOwner.setXRot(0);
            var beel=skill("beelzebuth:beelzebuth");var active=add(coneOwner,beel,10000);
            active.getOrCreateTag().putString("PrivateOwnerData","own-stomach");
            Mob mob=(Mob)BuiltInRegistries.ENTITY_TYPE.get(id("tensura:orc")).create(level);
            level.getChunk(625,625);mob.setPos(10000,120,10004);level.addFreshEntity(mob);
            for(var s:skills){if(s==beel)continue;var instance=s.createDefaultInstance();instance.setMastery(99);SkillAPI.getSkillsFrom(mob).updateSkill(instance,false);}
            active.getSkill().onHeld(active,coneOwner,1,0);
            check(skills.stream().allMatch(s->SkillAPI.getSkillsFrom(coneOwner).getSkill(s).isPresent()),"Real Beelzebuth held-predation path copies every mob skill in its cone");
            check(active.getOrCreateTag().getString("PrivateOwnerData").equals("own-stomach"),"Predation preserves the recipient existing Beelzebuth stomach data");
            check(recording(coneOwner).announcements.size()==skills.size()-1,"Mob predation emits a Voice payload for every newly copied skill");
            mob.discard();
            System.out.println("[BEEL-VERIFY] RESULT skills="+skills.size()+" passed="+passed+" findings="+findings+" errors=0");
        }catch(Throwable failure){failure.printStackTrace();System.out.println("[BEEL-VERIFY] RESULT errors=1");}
        finally{server.execute(()->server.halt(false));}
    }
    public static final class FaultySkill extends ManasSkill{
        @Override public void onLearnSkill(ManasSkillInstance instance,LivingEntity owner){throw new IllegalStateException("Intentional initializer failure");}
    }
    private static final class RecordingListener extends ServerGamePacketListenerImpl{
        final List<SkillAnnouncePayload> announcements=new ArrayList<>();
        RecordingListener(MinecraftServer server,ServerPlayer player){super(server,new Connection(PacketFlow.SERVERBOUND),player,CommonListenerCookie.createInitial(player.getGameProfile(),false));}
        @Override public void send(Packet<?> packet){
            if(packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof SkillAnnouncePayload announcement)announcements.add(announcement);
        }
    }
}
