package team.civitatum.charactercreation;

/**
 * Which birthsign a saved character gets ("10b", "Как получить знак"): one sign per character, for good. A character
 * without one (saved before the thirteen figures, or with a rewritten sky) chooses a month or draws a lot; the Serpent
 * comes only by lot or from an administrator.
 */
public final class SignChoice {
    /**
     * @param saved  the character as the server has it
     * @param wanted the sign the editor sent
     * @param roll   a random number in [0, 1) for a lot
     * @return the sign to save
     */
    public static String resolve(CharacterData saved, String wanted, EditorMode mode, double roll) {
        String current = saved.selected ? saved.birthsign : BirthsignCatalog.NONE;
        if (!current.equals(BirthsignCatalog.NONE)) {
            if (!wanted.equals(current)) throw new IllegalArgumentException(Civitatum.ERROR + "sign_locked");
            return current;
        }
        var sign = BirthsignCatalog.get(wanted);
        if (sign == null || sign.kind() == BirthsignCatalog.Kind.NONE) {
            // The mirror changes only looks: an old character keeps going without a sign until the full editor.
            if (mode == EditorMode.COSMETIC) return BirthsignCatalog.NONE;
            throw new IllegalArgumentException(Civitatum.ERROR + "sign_required");
        }
        if (sign.kind() == BirthsignCatalog.Kind.LOT) return BirthsignCatalog.draw(roll);
        if (!sign.isChoosable()) throw new IllegalArgumentException(Civitatum.ERROR + "sign_serpent");
        return sign.id();
    }

    private SignChoice() {}
}
