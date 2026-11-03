package ar.juan.beelzebuthbow;

import java.math.BigDecimal;
import net.minecraft.world.item.ItemStack;

/** Shared by the actual beam and its tooltip. Stored EP and legacy stages stay intact. */
public final class BowBalance {
    public static final BigDecimal EP_DAMAGE_RATE = new BigDecimal("0.00001");

    private BowBalance() {}

    public static int baseDamage(int stage) {
        return 10 * (Math.max(0, Math.min(5, stage)) + 1);
    }

    public static int baseDamage(ItemStack stack) {
        return baseDamage(BowProgress.stage(stack));
    }

    public static BigDecimal exactDamage(BigDecimal points, int stage) {
        return BigDecimal.valueOf(baseDamage(stage)).add(points.max(BigDecimal.ZERO).multiply(EP_DAMAGE_RATE));
    }

    public static double damage(ItemStack stack, float charge, int power) {
        double fraction = Float.isFinite(charge) ? Math.max(0.0, Math.min(1.0, charge)) : 0.0;
        // Preserve the original charge curve and 1.2 damage per Power level, without
        // multiplying the new flat stage damage by the old percentage bands or 0.1.
        BigDecimal result = exactDamage(BowProgress.exactPoints(stack), BowProgress.stage(stack))
                .multiply(BigDecimal.valueOf(0.35 + 0.65 * fraction))
                .add(BigDecimal.valueOf(Math.max(0, power) * 1.2));
        return Math.min(Double.MAX_VALUE, result.doubleValue());
    }
}
