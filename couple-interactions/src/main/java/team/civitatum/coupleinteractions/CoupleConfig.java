package team.civitatum.coupleinteractions;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CoupleConfig {
    public enum ObstacleMode { STRICT, RELAXED, OFF }
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue REACH, CARRY_SPEED;
    public static final ModConfigSpec.IntValue REQUEST_SECONDS, COOLDOWN_TICKS;
    public static final ModConfigSpec.BooleanValue ALLOW_CARRY, ALLOW_KISS,ALLOW_HUG,ALLOW_REST;
    public static final ModConfigSpec.EnumValue<ObstacleMode> CARRY_OBSTACLE_MODE;
    public static final ModConfigSpec.IntValue CARRY_OBSTACLE_GRACE;
    public static final ModConfigSpec.DoubleValue CARRY_OBSTACLE_TOLERANCE;
    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("interactions");
        ALLOW_CARRY = b.comment("Allow bridal carrying on this server.").define("allowCarry", true);
        ALLOW_KISS = b.comment("Allow cosmetic kissing on this server.").define("allowKiss", true);
        ALLOW_HUG=b.define("allowHug",true);ALLOW_REST=b.define("allowRestTogether",true);
        REACH = b.comment("Maximum distance between players. Checked again when accepting.").defineInRange("reach", 2.5, 1.0, 4.0);
        REQUEST_SECONDS = b.defineInRange("requestTimeoutSeconds", 12, 3, 60);
        COOLDOWN_TICKS = b.comment("Minimum interval between requests from one player; 20 ticks = 1 second.").defineInRange("requestCooldownTicks", 60, 20, 1200);
        CARRY_SPEED = b.comment("Movement speed multiplier while carrying.").defineInRange("carryingSpeedMultiplier", 0.8, 0.1, 1.0);
        CARRY_OBSTACLE_MODE = b.comment("STRICT: release on full passenger hitbox contact. RELAXED: ignore low blocks and brief contact. OFF: never release because of blocks.")
                .defineEnum("carryObstacleMode", ObstacleMode.RELAXED);
        CARRY_OBSTACLE_GRACE = b.comment("Consecutive obstructed ticks before release in RELAXED mode; 20 ticks = 1 second.")
                .defineInRange("carryObstacleGraceTicks", 10, 1, 200);
        CARRY_OBSTACLE_TOLERANCE = b.comment("Hitbox inset in blocks at normal player scale, used only in RELAXED mode.")
                .defineInRange("carryObstacleTolerance", 0.3, 0.0, 0.7);
        b.pop();
        SPEC = b.build();
    }
    private CoupleConfig() {}
}
