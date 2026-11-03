package local.tensura.crashreview;

import com.mojang.authlib.GameProfile;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.manasmods.manascore.race.api.RaceAPI;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.UUID;

@Mod("tensura_crash_review")
public final class RaceRegistryProbe {
    private final java.util.concurrent.atomic.AtomicBoolean ran=new java.util.concurrent.atomic.AtomicBoolean();
    public RaceRegistryProbe() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event)->{
            if(dev.architectury.platform.Platform.getEnvironment()==dev.architectury.utils.Env.CLIENT) {
                Thread fallback=new Thread(()->{
                    try{Thread.sleep(20000);}catch(InterruptedException ex){return;}
                    event.getServer().execute(()->{if(ran.compareAndSet(false,true))run(event.getServer());});
                },"Isolated review fallback");fallback.setDaemon(true);fallback.start();
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if(event.getServer().getTickCount()>=100 && ran.compareAndSet(false,true)) {
                run(event.getServer());
            }
        });
    }
    private static void run(net.minecraft.server.MinecraftServer server) {
        if (!Boolean.getBoolean("tensura.crash.review")) return;
        int failures=0;
        try {
            String[][] fields={
                {"com.github.propheticeclipse.tensurastarlight.registry.StarlightRaces","ASPECT_OF_REGRESSION"},
                {"io.github.Memoires.mysticism.registry.race.MysticismRaces","INSECT"},
                {"io.github.Memoires.mysticism.registry.race.MysticismRaces","TRUE_DRAGONOID"}
            };
            for (String[] field:fields) {
                RegistrySupplier<?> supplier=(RegistrySupplier<?>)Class.forName(field[0]).getField(field[1]).get(null);
                Object fromRegistry=RaceAPI.getRaceRegistry().get(supplier.getId());
                Object fromSupplier=null;
                try {fromSupplier=supplier.get();} catch (Exception ex) {System.out.println("[CRASH-REVIEW] supplier error: "+ex);}
                boolean ok=fromRegistry!=null && fromSupplier==fromRegistry;
                System.out.println("[CRASH-REVIEW] "+(ok?"PASS":"OBSERVED_ABSENT")+" "+supplier.getId()+" present="+supplier.isPresent()+" direct="+(fromRegistry!=null)+" supplier="+(fromSupplier!=null));
                var registrar=supplier.getRegistrar();
                var registryField=registrar.getClass().getDeclaredField("registry");registryField.setAccessible(true);
                var pending=(java.util.Map<?,?>)registryField.get(registrar);
                var data=pending.get(registrar.key());
                if(data!=null){
                    var registered=data.getClass().getDeclaredField("registered");registered.setAccessible(true);
                    var objects=data.getClass().getDeclaredField("objects");objects.setAccessible(true);
                    System.out.println("[CRASH-REVIEW] registry state registered="+registered.get(data)+" queued="+((java.util.Map<?,?>)objects.get(data)).size());
                }
            }
            var player=FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"CrashReview"));
            try {
                boolean set=RaceAPI.getRaceFrom(player).setRace(RaceAPI.getRaceRegistry().get(ResourceLocation.parse("tensura:slime")),false);
                if(!set) throw new IllegalStateException("setRace returned false");
                System.out.println("[CRASH-REVIEW] PASS reincarnation to slime without resetting real players");
            } catch(Exception ex) {if(Boolean.getBoolean("tensura.crash.guarded"))failures++;ex.printStackTrace();}
            for(String[] lookup:new String[][]{
                {"com.github.propheticeclipse.tensurastarlight.registry.StarlightRaces","ASPECT_OF_REGRESSION"},
                {"io.github.Memoires.mysticism.registry.race.MysticismRaces","INSECT"}
            }) {
                RegistrySupplier<?> supplier=(RegistrySupplier<?>)Class.forName(lookup[0]).getField(lookup[1]).get(null);
                org.apache.commons.lang3.mutable.Mutable mutable=findMutable(supplier,0);
                if(mutable==null)throw new IllegalStateException("Could not locate isolated supplier state");
                Object original=mutable.getValue();mutable.setValue(null);
                try {
                    if(supplier.isPresent())throw new IllegalStateException("Absent supplier simulation failed");
                    boolean threw=false;
                    try {
                        if(lookup[1].equals("ASPECT_OF_REGRESSION")) {
                            Skill lethe=(Skill)SkillAPI.getSkillRegistry().get(ResourceLocation.parse("trstarlight:lethe_lady_of_phantasm"));
                            if(lethe.checkAcquiringRequirement(player,0))throw new IllegalStateException("Lethe unlocked without race");
                        }else {
                            var alternate=FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"MissingRaceTest"));
                            if(!RaceAPI.getRaceFrom(alternate).setRace(RaceAPI.getRaceRegistry().get(ResourceLocation.parse("tensura:slime")),false))throw new IllegalStateException("Race assignment rejected");
                        }
                    }catch(NullPointerException ex){threw=true;System.out.println("[CRASH-REVIEW] reproduced "+ex.getMessage());}
                    boolean guarded=Boolean.getBoolean("tensura.crash.guarded");
                    if(threw==guarded){failures++;System.out.println("[CRASH-REVIEW] FAIL absent supplier "+supplier.getId());}
                    else System.out.println("[CRASH-REVIEW] PASS "+(guarded?"guard handles":"baseline reproduces")+" absent supplier "+supplier.getId());
                }finally{mutable.setValue(original);}
                if(original==null ? supplier.isPresent() : supplier.get()!=original)throw new IllegalStateException("Isolated supplier was not restored");
                System.out.println("[CRASH-REVIEW] PASS supplier restored "+supplier.getId());
            }
            try {
                Skill lethe=(Skill)SkillAPI.getSkillRegistry().get(ResourceLocation.parse("trstarlight:lethe_lady_of_phantasm"));
                boolean eligible=lethe.checkAcquiringRequirement(player,0);
                if(eligible)throw new IllegalStateException("Untrained slime met Lethe requirements");
                System.out.println("[CRASH-REVIEW] PASS Lethe requirement evaluates false without crashing");
            } catch(Exception ex) {if(Boolean.getBoolean("tensura.crash.guarded"))failures++;ex.printStackTrace();}
        } catch(Exception ex) {failures++;ex.printStackTrace();}
        System.out.println("[CRASH-REVIEW] COMPLETE failures="+failures);
        server.halt(false);
        if(dev.architectury.platform.Platform.getEnvironment()==dev.architectury.utils.Env.CLIENT) {
            try {
                Class<?> minecraft=Class.forName("net.minecraft.client.Minecraft");
                Object instance=minecraft.getMethod("getInstance").invoke(null);
                ((java.util.concurrent.Executor)instance).execute(()->{try{minecraft.getMethod("stop").invoke(instance);}catch(Exception ex){throw new RuntimeException(ex);}});
            }catch(Exception ex){ex.printStackTrace();}
        }
    }
    private static org.apache.commons.lang3.mutable.Mutable findMutable(Object value,int depth)throws Exception {
        if(value instanceof org.apache.commons.lang3.mutable.Mutable mutable)return mutable;
        if(value==null || depth>3 || value.getClass().getName().startsWith("java."))return null;
        for(var field:value.getClass().getDeclaredFields()) {
            if(java.lang.reflect.Modifier.isStatic(field.getModifiers()))continue;
            String type=field.getType().getName();
            if(!type.contains("Supplier") && !type.contains("Mutable"))continue;
            field.setAccessible(true);var found=findMutable(field.get(value),depth+1);if(found!=null)return found;
        }
        return null;
    }
}
