package team.civitatum.starclock.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import team.civitatum.starclock.prediction.PredictionTimer;

public final class StarClockConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue COOLDOWN_DAYS;
    public static final ModConfigSpec.IntValue TIMER_MIN;
    public static final ModConfigSpec.IntValue TIMER_MAX;
    public static final ModConfigSpec.IntValue TIMER_THIRD_MAX;
    public static final ModConfigSpec.BooleanValue DESTRUCTIVE_EVENTS;
    public static final ModConfigSpec.IntValue TRADER_LIFETIME;
    public static final ModConfigSpec.ConfigValue<String> WEBHOOK_URL;

    static {
        var builder = new ModConfigSpec.Builder();
        COOLDOWN_DAYS = builder.comment("Общий кулдаун в игровых сутках.").defineInRange("cooldownDays", 8, 1, 3650);
        TIMER_MIN = builder.comment("Минимальная задержка, тики.").defineInRange("timerMinTicks", 6000, 1, 24000000);
        TIMER_MAX = builder.comment("Максимальная задержка I и II ступеней, тики.").defineInRange("timerMaxTicks", 96000, 1, 24000000);
        TIMER_THIRD_MAX = builder.comment("Максимальная задержка III ступени, тики.").defineInRange("timerThirdMaxTicks", 72000, 1, 24000000);
        DESTRUCTIVE_EVENTS = builder.comment("Разрешить блоковые действия из дополнительных таблиц. По умолчанию выключено.")
                .define("destructiveEvents", false);
        TRADER_LIFETIME = builder.comment("Время жизни торговца, секунды.").defineInRange("traderLifetimeSeconds", 600, 30, 86400);
        WEBHOOK_URL = builder.comment("Адрес Discord-вебхука; пустая строка отключает отправку.")
                .define("webhookUrl", "");
        SPEC = builder.build();
    }

    public static void validate() {
        PredictionTimer.validate(TIMER_MIN.get(), TIMER_MAX.get(), TIMER_THIRD_MAX.get());
    }

    private StarClockConfig() {}
}
