package local.tensura.verify;

import com.mojang.authlib.GameProfile;
import io.github.manasmods.manascore.race.api.*;
import io.github.manasmods.manascore.skill.api.*;
import io.github.manasmods.tensura.race.RaceHelper;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.storage.TensuraStorages;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import java.util.*;

@Mod("tensura_evolution_verification")
public final class InsectEvolutionVerification {
    public InsectEvolutionVerification() {
        // Dynamic race registries become available after all ServerStarted listeners.
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event)->event.getServer().execute(()->verify(event)));
    }
    private static ResourceLocation id(String s) { return ResourceLocation.parse(s); }
    private void verify(ServerStartedEvent event) {
        if (!Boolean.getBoolean("tensura.verify")) return;
        int count=0, failures=0;
        var server=event.getServer();
        var targets=List.of("divine_fire_ant","divine_hardshell_ant","divine_stag_beetle","divine_drone_beetle",
            "divine_blue_centipede","divine_purple_centipede","divine_yellow_centipede","divine_lixivant_mantis",
            "divine_preying_mantis","divine_loxodrome_scorpion","divine_singularity_scorpion","divine_army_wasp","divine_queen_wasp","trorigins:insect_dragon");
        try {
            var probeAccess=FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"VerifyAnalysis"));
            if((Boolean)Class.forName("dev.dedek.greatsage.GreatSageGameRules").getMethod("access",net.minecraft.world.entity.LivingEntity.class).invoke(null,probeAccess)) throw new IllegalStateException("Original Great Sage skill requirement was bypassed");
            if((Boolean)Class.forName("dev.dedek.greatsage.server.SageProgress").getMethod("rankAllowed",net.minecraft.server.level.ServerPlayer.class,String.class).invoke(null,probeAccess,"ultimate")) throw new IllegalStateException("Ultimate analysis rank was unlocked for free");
            System.out.println("[INSECT-VERIFY] PASS original Great Sage skill requirement and locked ultimate rank");
            var sourceItem=Items.IRON_SWORD.getDefaultInstance();var destination=Items.DIAMOND_SWORD.getDefaultInstance();
            var sourceAttributes=sourceItem.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.EMPTY);
            var destinationAttributes=destination.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.EMPTY);
            var origin=sourceAttributes.modifiers().stream().filter(e->e.attribute().is(Attributes.ATTACK_DAMAGE)).findFirst().orElseThrow();
            double destinationDamage=destinationAttributes.modifiers().stream().filter(e->e.attribute().is(Attributes.ATTACK_DAMAGE)).findFirst().orElseThrow().modifier().amount();
            sourceItem.set(DataComponents.ATTRIBUTE_MODIFIERS,sourceAttributes.withModifierAdded(origin.attribute(),new AttributeModifier(origin.modifier().id(),origin.modifier().amount()+5,origin.modifier().operation()),origin.slot()));
            Class.forName("net.studio.redraven.tensura_ep_scaling.common.EvolutionComponents").getMethod("copyForEvolution",net.minecraft.world.item.ItemStack.class,net.minecraft.world.item.ItemStack.class).invoke(null,destination,sourceItem);
            double actualDamage=destination.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream().filter(e->e.attribute().is(Attributes.ATTACK_DAMAGE)).findFirst().orElseThrow().modifier().amount();
            if(actualDamage!=destinationDamage+5) throw new IllegalStateException("Evolved item damage lost its base or custom bonus");
            System.out.println("[INSECT-VERIFY] PASS retained evolved-weapon base damage and custom bonuses");
            var epItem=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id("mysticism:axiom"));
            if(epItem==Items.AIR || !(Boolean)Class.forName("net.studio.redraven.tensura_ep_scaling.common.gear_ep.EPItemHandler")
                .getMethod("hasEP",net.minecraft.world.item.Item.class).invoke(null,epItem))
                throw new IllegalStateException("Gear JSON with omitted maxEP was not loaded");
            System.out.println("[INSECT-VERIFY] PASS gear JSON optional maxEP uses Tensura default");
            var training=FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"VerifyMastery"));
            training.getAttribute(TensuraAttributes.ABILITY_LEARNING_GAIN).setBaseValue(1);
            training.getAttribute(TensuraAttributes.ABILITY_MASTERY_GAIN).setBaseValue(1);
            var strength=SkillAPI.getSkillRegistry().get(id("tensura:strength"));
            ManasSkillInstance strengthInstance=strength.createDefaultInstance();
            strengthInstance.setMastery(0);
            String[] boosters={"analyst","cook","great_sage"};
            double[] bonuses={2,4,9};
            for(int i=0;i<boosters.length;i++) {
                var boost=SkillAPI.getSkillRegistry().get(id("tensura:"+boosters[i]));
                ManasSkillInstance boostInstance=boost.createDefaultInstance();
                boostInstance.setMastery(0);
                boost.onToggleOn(boostInstance,training);
                double expected=1+bonuses[i];
                if(training.getAttributeValue(TensuraAttributes.ABILITY_LEARNING_GAIN)!=expected
                    || training.getAttributeValue(TensuraAttributes.ABILITY_MASTERY_GAIN)!=expected)
                    throw new IllegalStateException("Incorrect training bonus: "+boosters[i]);
                strengthInstance.setMastery(0);
                strengthInstance.addMasteryPoint(training);
                if(strengthInstance.getMastery()!=expected) throw new IllegalStateException("Incorrect mastery gain: "+boosters[i]);
                boost.onToggleOff(boostInstance,training);
                if(training.getAttributeValue(TensuraAttributes.ABILITY_MASTERY_GAIN)!=1)
                    throw new IllegalStateException("Training bonus persisted while disabled");
                System.out.println("[INSECT-VERIFY] PASS training "+boosters[i]+" learning and mastery bonus="+bonuses[i]);
            }
            for (String name:targets) {
                var target=RaceAPI.getRaceRegistry().get(id(name.contains(":")?name:"mysticism:"+name));
                if (target==null) throw new IllegalStateException("Missing race "+name);
                var probe=FakePlayerFactory.get(server.overworld(), new GameProfile(UUID.randomUUID(),"VerifyInsect"));
                var predecessors=target.getPreviousEvolutions(target.createDefaultInstance(),probe);
                for (var source:predecessors) {
                    count++;
                    String label=source.getRegistryName()+" -> "+target.getRegistryName();
                    try {
                        var player=FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"VerifyInsect"));
                        var races=RaceAPI.getRaceFrom(player);
                        if(!races.setRace(source,false)) throw new IllegalStateException("Cannot set source race");
                        player.getAttribute(TensuraAttributes.MAX_AURA).setBaseValue(20_000_000);
                        player.getAttribute(TensuraAttributes.MAX_MAGICULE).setBaseValue(20_000_000);
                        player.experienceLevel=37;
                        var data=TensuraStorages.getPlayerDataFrom(player);
                        data.setResetCounter(17);data.setInResetProgress(0);
                        var skills=SkillAPI.getSkillsFrom(player);
                        var sentinel=SkillAPI.getSkillRegistry().get(id("tensura:great_sage"));
                        skills.learnSkill(sentinel);
                        Set<ManasSkill> prior=new HashSet<>();
                        for(var learned:skills.getLearnedSkills()) prior.add(learned.getSkill());
                        if(!races.getRace().orElseThrow().getNextEvolutions(player).contains(target)) throw new IllegalStateException("Missing evolution link");
                        if(!RaceHelper.evolveRace(player,target,true)) throw new IllegalStateException("Evolution rejected");
                        if(races.getRace().orElseThrow().getRace()!=target) throw new IllegalStateException("Race was reset/replaced");
                        if(ManasRaceInstance.fromNBT(races.getRace().orElseThrow().toNBT()).getRace()!=target) throw new IllegalStateException("Race did not survive serialization");
                        if(data.getResetCounter()!=17 || data.getInResetProgress()!=0 || player.experienceLevel!=37) throw new IllegalStateException("Character progress was reset");
                        if(player.getAttribute(TensuraAttributes.MAX_AURA).getBaseValue()<20_000_000 || player.getAttribute(TensuraAttributes.MAX_MAGICULE).getBaseValue()<20_000_000) throw new IllegalStateException("EP was lost");
                        for(var skill:prior) if(skills.getSkill(skill).isEmpty()) throw new IllegalStateException("Lost skill "+skill.getRegistryName());
                        var expected=target.getIntrinsicSkills(races.getRace().orElseThrow(),player);
                        for(var skill:expected) if(skills.getSkill(skill).isEmpty()) throw new IllegalStateException("Missing new skill "+skill.getRegistryName());
                        System.out.println("[INSECT-VERIFY] PASS "+label+" | intrinsic skills="+expected.size()+" | retained="+prior.size());
                    } catch(Throwable error) {
                        failures++;System.out.println("[INSECT-VERIFY] FAIL "+label+" | "+error);error.printStackTrace();
                    }
                }
            }
        } catch(Throwable error) { failures++;error.printStackTrace(); }
        System.out.println("[INSECT-VERIFY] RESULT cases="+count+" failures="+failures);
        server.execute(()->server.halt(false));
    }
}
