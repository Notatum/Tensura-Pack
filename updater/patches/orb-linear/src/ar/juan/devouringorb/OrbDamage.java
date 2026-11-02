package ar.juan.devouringorb;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** One formula for combat, the tooltip and the sound screen; never modifies saved EP. */
public final class OrbDamage {
    public static BigDecimal exact(ItemStack orb) {
        return OrbBalance.exactDamage(OrbProgress.exactPoints(orb), OrbProgress.stage(orb));
    }

    public static double damage(ItemStack orb) {
        return Math.min(Double.MAX_VALUE, exact(orb).doubleValue());
    }

    public static String formatted(ItemStack orb) { return format(exact(orb)); }

    public static String format(BigDecimal value) {
        if (value.abs().compareTo(new BigDecimal("1000000000000")) >= 0)
            return value.stripTrailingZeros().toEngineeringString();
        DecimalFormat format = new DecimalFormat("#,##0.######", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-AR")));
        return format.format(value);
    }

    public static void explain(ItemStack orb, List<Component> lines) {
        lines.add(Component.translatable("tooltip.devouring_orb.damage_base", format(BigDecimal.valueOf(OrbBalance.baseDamage(OrbProgress.stage(orb)))))
            .withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(Component.translatable("tooltip.devouring_orb.damage_ep", format(OrbProgress.exactPoints(orb).multiply(OrbBalance.EP_DAMAGE_RATE)), "0,001")
            .withStyle(ChatFormatting.DARK_PURPLE));
        lines.add(Component.translatable("tooltip.devouring_orb.damage_growth").withStyle(ChatFormatting.GRAY));
    }
}
