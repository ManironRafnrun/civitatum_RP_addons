package ru.teacraft.civitatum;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AppearanceRegressionTest {
    @Test void idleRunsAtHalfSpeedAndHoldsForTwoSecondsBetweenCycles() {
        assertEquals(125,IdleAnimation.playbackTime(250));
        assertEquals(249.5f,IdleAnimation.playbackTime(499));
        for(float t:new float[]{500,501,520,539.99f,1010,1030,1049.99f})assertEquals(250,IdleAnimation.playbackTime(t));
        assertEquals(15,IdleAnimation.playbackTime(540));
        assertEquals(16,IdleAnimation.playbackTime(542));
        assertEquals(249.5f,IdleAnimation.playbackTime(1009));
        assertEquals(15,IdleAnimation.playbackTime(1050));
        for(float t:new float[]{-1,Float.NaN,Float.POSITIVE_INFINITY})assertEquals(0,IdleAnimation.playbackTime(t));
    }
    @Test void matchingClothingHidesOnlyTheCorrespondingUnderwearPiece() {
        var c=RaceCatalog.defaults("human","female");c.wardrobe.clear();c.wardrobeEnabled=true;
        assertEquals(new UnderwearVisibility(true,true),UnderwearVisibility.of(c));
        c.wardrobe.put("trousers",new CharacterData.Garment("many_layers/trousers"));
        assertEquals(new UnderwearVisibility(true,false),UnderwearVisibility.of(c));
        c.wardrobe.clear();c.wardrobe.put("shirt",new CharacterData.Garment("ladylike/blouse"));
        assertEquals(new UnderwearVisibility(false,true),UnderwearVisibility.of(c));
        c.wardrobe.put("skirt",new CharacterData.Garment("ladylike/skirt"));
        assertEquals(new UnderwearVisibility(false,false),UnderwearVisibility.of(c));
        c.naked=true;assertEquals(new UnderwearVisibility(true,true),UnderwearVisibility.of(c));
        c.naked=false;c.wardrobeEnabled=false;assertEquals(new UnderwearVisibility(true,true),UnderwearVisibility.of(c));
    }
    @Test void dressesHideBothPiecesButAccessoriesDoNot() {
        var c=RaceCatalog.defaults("human","female");
        for(String id:List.of("verer_daelaf","mistress_arrives","sunset")) {
            AppearanceCatalog.outfit(c,id);assertEquals(new UnderwearVisibility(false,false),UnderwearVisibility.of(c),id);
        }
        c.wardrobe.clear();c.wardrobe.put("boots",new CharacterData.Garment("aegipants/boots"));
        c.wardrobe.put("belt",new CharacterData.Garment("aegipants/belt"));
        assertEquals(new UnderwearVisibility(true,true),UnderwearVisibility.of(c));
        c.wardrobe.put("trousers",new CharacterData.Garment("ruby_sash/trousers"));
        assertEquals(new UnderwearVisibility(true,true),UnderwearVisibility.of(c),"wrong sex is not rendered");
        c.wardrobe.put("trousers",new CharacterData.Garment("unknown"));
        assertEquals(new UnderwearVisibility(true,true),UnderwearVisibility.of(c));
    }
}
