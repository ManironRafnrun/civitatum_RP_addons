package team.civitatum.menu;

import java.time.LocalDate;
import java.util.Locale;

public enum Season {
    SPRING,
    SUMMER,
    FALL,
    WINTER;

    public static Season at(LocalDate date, boolean southernHemisphere) {
        int month = date.getMonthValue();
        Season season = month >= 3 && month <= 5 ? SPRING : (month >= 6 && month <= 8 ? SUMMER : (month >= 9 && month <= 11 ? FALL : WINTER));
        return southernHemisphere ? values()[(season.ordinal() + 2) % 4] : season;
    }

    public static Season resolve(String override, LocalDate date, boolean southernHemisphere) {
        if (override != null && !override.equalsIgnoreCase("auto")) {
            try {
                return valueOf(override.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                // unknown name: fall back to the calendar
            }
        }

        return at(date, southernHemisphere);
    }

    public String fileName() {
        return this.name().toLowerCase(Locale.ROOT) + "_background.png";
    }
}
