package local.tensura.sagevanillaverify;

import com.mojang.authlib.GameProfile;
import dev.dedek.greatsage.GreatSageCommonConfig;
import dev.dedek.greatsage.GreatSageGameRules;
import dev.dedek.greatsage.GreatSageTip;
import dev.dedek.greatsage.SageNode;
import dev.dedek.greatsage.network.TipPayload;
import dev.dedek.greatsage.server.SageProgress;
import dev.dedek.greatsage.server.TipWatcher;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("sage_vanilla_verification")
public final class GreatSageVanillaVerification {
    private boolean started;private int checks;
    public GreatSageVanillaVerification(){
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event)->{
            if(!started && Boolean.getBoolean("tensura.sage.vanilla.verify")){started=true;run(event.getServer());}
        });
    }
    private void check(boolean value,String description){if(!value)throw new IllegalStateException(description);checks++;System.out.println("[SAGE-VANILLA] PASS "+description);}
    private void run(MinecraftServer server){
        try{
            var level=server.overworld();
            var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"VanillaSage"));player.tickCount=300;
            var connection=new RecordingListener(server,player);player.connection=connection;
            check(level.getGameRules().getBoolean(GreatSageGameRules.REQUIRE_GREAT_SAGE),"Original requireGreatSage rule remains enabled");
            check(!GreatSageGameRules.access(player),"Great Sage tools require the skill again");
            check(!TipWatcher.fire(player,GreatSageTip.values()[0]),"No ambient tip can fire without Great Sage");
            check(connection.tips==0,"No ambient Voice packet is sent to a player without Great Sage");
            var skill=SkillAPI.getSkillRegistry().get(ResourceLocation.parse("tensura:great_sage"));
            var instance=skill.createDefaultInstance();instance.setMastery(-50);SkillAPI.getSkillsFrom(player).updateSkill(instance,false);
            check(!GreatSageGameRules.access(player) && !TipWatcher.fire(player,GreatSageTip.values()[0]),"A skill still in learning does not unlock Sage tips");
            instance.setMastery(0);SkillAPI.getSkillsFrom(player).updateSkill(instance,false);
            check(GreatSageGameRules.access(player),"Actually learning Great Sage restores access");
            check(TipWatcher.fire(player,GreatSageTip.values()[0]) && connection.tips==1,"Original ambient tip works once the skill is acquired");
            check(!SageProgress.has(player,SageNode.RANK_ULTIMATE),"Ultimate analysis rank is no longer unlocked for free");
            check(Math.abs(GreatSageCommonConfig.ANALYSIS_FAIL_CHANCE.get()-0.25)<0.000001,"Fresh official common configuration uses the author default failure chance");
            check(GreatSageGameRules.allowed(player),"Normal acquisition announcements retain official behavior");
            System.out.println("[SAGE-VANILLA] RESULT checks="+checks+" errors=0");
        }catch(Throwable error){error.printStackTrace();System.out.println("[SAGE-VANILLA] RESULT errors=1");}
        finally{server.execute(()->server.halt(false));}
    }
    private static final class RecordingListener extends ServerGamePacketListenerImpl{
        int tips;
        RecordingListener(MinecraftServer server,ServerPlayer player){super(server,new Connection(PacketFlow.SERVERBOUND),player,CommonListenerCookie.createInitial(player.getGameProfile(),false));}
        @Override public void send(Packet<?> packet){if(packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof TipPayload)tips++;}
    }
}
