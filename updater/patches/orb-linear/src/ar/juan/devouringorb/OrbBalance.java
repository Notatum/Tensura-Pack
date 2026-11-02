/*
 * Decompiled with CFR 0.152.
 */
package ar.juan.devouringorb;

import java.math.BigDecimal;

public final class OrbBalance {
    public static final double MAX_RADIUS = 50.0;
    public static final double MOB_EP_RATE = 0.08;
    public static final BigDecimal EP_DAMAGE_RATE = new BigDecimal("0.00001");
    private static final double[] THRESHOLDS = new double[]{0.0, 5000.0, 15000.0, 45000.0, 105000.0, 225000.0, 450000.0, 900000.0, 1650000.0, 4500000.0};
    private static final double[] DAMAGE_BANDS = new double[]{0.0, 5000.0, 35000.0, 150000.0, 550000.0, 1500000.0};
    private static final int[] DAMAGE_BONUS = new int[]{10, 20, 35, 50, 70, 100};
    private static final int[] COOLDOWN_TICKS = new int[]{45, 44, 42, 39, 36, 33, 30, 30, 30, 30};
    private static final double[] RADII = new double[]{15.0, 18.0, 22.0, 25.0, 29.0, 32.0, 36.0, 39.0, 43.0, 50.0};
    private static final int[] OLD_STAGES = new int[]{0, 2, 4, 6, 8, 9};

    private OrbBalance() {
    }

    public static int stage(double points) {
        for (int i = THRESHOLDS.length - 1; i >= 0; --i) {
            if (!(points >= THRESHOLDS[i])) continue;
            return i;
        }
        return 0;
    }

    public static int safeStage(int stage) {
        return Math.max(0, Math.min(OrbBalance.stages() - 1, stage));
    }

    public static double threshold(int stage) {
        return THRESHOLDS[OrbBalance.safeStage(stage)];
    }

    public static int stages() {
        return THRESHOLDS.length;
    }

    public static int cooldown(int stage) {
        return COOLDOWN_TICKS[OrbBalance.safeStage(stage)];
    }

    public static int bonus(int band) {
        return DAMAGE_BONUS[Math.max(0, Math.min(DAMAGE_BONUS.length - 1, band))];
    }

    public static double radius(int stage) {
        return RADII[OrbBalance.safeStage(stage)];
    }

    static int migratedStage(int oldStage) {
        return OLD_STAGES[Math.max(0, Math.min(OLD_STAGES.length - 1, oldStage))];
    }

    static double legacyFloor(int oldStage) {
        return DAMAGE_BANDS[Math.max(0, Math.min(DAMAGE_BANDS.length - 1, oldStage))];
    }

    static int damageBand(double points) {
        for (int i = DAMAGE_BANDS.length - 1; i >= 0; --i) {
            if (!(points >= DAMAGE_BANDS[i])) continue;
            return i;
        }
        return 0;
    }

    public static double damage(BigDecimal points) {
        return Math.min(Double.MAX_VALUE, exactDamage(points, stage(points.doubleValue())).doubleValue());
    }

    public static int baseDamage(int stage) { return 5 * (safeStage(stage) + 1); }

    public static BigDecimal exactDamage(BigDecimal points, int stage) {
        return BigDecimal.valueOf(baseDamage(stage)).add(points.max(BigDecimal.ZERO).multiply(EP_DAMAGE_RATE));
    }

    public static double killEp(double mobEp, String namespace, boolean player) {
        return !player && (namespace.equals("tensura") || namespace.equals("mysticism")) && Double.isFinite(mobEp) && mobEp > 0.0 ? mobEp * 0.08 : 0.0;
    }

    static boolean withinRadius(double distanceSquared, int stage) {
        double radius = OrbBalance.radius(stage);
        return Double.isFinite(distanceSquared) && distanceSquared >= 0.0 && distanceSquared <= radius * radius;
    }

    static boolean confirmedDeath(boolean canceled, boolean dying, boolean killedRemoval) {
        return !canceled && (dying || killedRemoval);
    }

    static BigDecimal parse(String text) {
        try {
            return new BigDecimal(text).max(BigDecimal.ZERO);
        }
        catch (NullPointerException | NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    public static double bob(double ticks) {
        return Math.sin(ticks * Math.PI / 40.0) * 0.012;
    }

    public static double sway(double ticks) {
        return Math.sin(ticks * Math.PI / 40.0) * 2.0;
    }
}
