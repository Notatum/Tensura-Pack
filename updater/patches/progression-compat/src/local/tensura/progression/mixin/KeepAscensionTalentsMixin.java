package local.tensura.progression.mixin;

import io.github.manasmods.tensura.item.misc.ResetScrollItem;
import java.util.Map;
import java.util.WeakHashMap;
import local.tensura.progression.AscensionTalents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResetScrollItem.class)
public abstract class KeepAscensionTalentsMixin {
    // These three audited reset implementations do not call each other. Weak keys
    // also release an interrupted reset without retaining a disconnected player.
    @Unique private static final Map<ServerPlayer, CompoundTag> tensura$talentResets = new WeakHashMap<>();

    @Inject(method={"resetEverything", "resetRace"}, at=@At("HEAD"), require=2)
    private static void tensura$saveTalents(ServerPlayer player, CallbackInfo ci) {
        CompoundTag saved = AscensionTalents.snapshot(player);
        if (saved != null) tensura$talentResets.put(player, saved);
        else tensura$talentResets.remove(player);
    }

    @Inject(method={"resetEverything", "resetRace"}, at=@At("RETURN"), require=2)
    private static void tensura$restoreTalents(ServerPlayer player, CallbackInfo ci) {
        AscensionTalents.restore(player, tensura$talentResets.remove(player));
        AscensionTalents.refresh(player);
    }

    @Inject(method="resetSkill(Lnet/minecraft/server/level/ServerPlayer;Z)V", at=@At("HEAD"), require=1)
    private static void tensura$saveSkillResetTalents(ServerPlayer player, boolean coverEP, CallbackInfo ci) {
        tensura$saveTalents(player, ci);
    }

    @Inject(method="resetSkill(Lnet/minecraft/server/level/ServerPlayer;Z)V", at=@At("RETURN"), require=1)
    private static void tensura$restoreSkillResetTalents(ServerPlayer player, boolean coverEP, CallbackInfo ci) {
        tensura$restoreTalents(player, ci);
    }
}
