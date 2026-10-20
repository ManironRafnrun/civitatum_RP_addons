package team.civitatum.books.server;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CharacterNamesTest {
    public static class Character {
        public boolean selected;
        public String name = "";
    }
    public record State(Character character) {}

    @Test void onlySelectedValidServerCharacterNamesAreUsed() {
        Character character = new Character();
        character.name = "  Рафн Городской  ";
        assertEquals("", CharacterNames.characterName(new State(character)));
        character.selected = true;
        assertEquals("Рафн Городской", CharacterNames.characterName(new State(character)));
        character.name = "\nПодмена";
        assertEquals("Подмена", CharacterNames.characterName(new State(character)));
        character.name = "Имя\nСуффикс";
        assertEquals("", CharacterNames.characterName(new State(character)));
        assertEquals("", CharacterNames.characterName(new Object()));
    }

    @Test void administratorSignatureWinsAndNetworkAuthorCannotForgeCharacterName() {
        assertEquals("Городской архив", BookSigning.author("Городской архив", "Подмена", "Рафн", false, true));
        assertEquals("Рафн", BookSigning.author("", "Подмена", "Рафн", false, true));
        assertEquals("Архив", BookSigning.author("", "Архив", "Рафн", true, true));
    }
}
