package team.civitatum.starclock.item;

import net.minecraft.world.item.Rarity;

public enum ToolTier {
    I(1, Rarity.COMMON), II(2, Rarity.UNCOMMON), III(3, Rarity.RARE);

    private final int strength;
    private final Rarity rarity;

    ToolTier(int strength, Rarity rarity) {
        this.strength = strength;
        this.rarity = rarity;
    }

    public int strength() {
        return strength;
    }

    public Rarity rarity() {
        return rarity;
    }
}
