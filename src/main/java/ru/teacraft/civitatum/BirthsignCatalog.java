package ru.teacraft.civitatum;

import java.util.*;

/** Initial Civitatum balance templates inspired by TES III archetypes, not copied game statistics. */
public final class BirthsignCatalog {
    public record Sign(String id,String name,String description,String effect) {}
    private static final List<Sign> ALL=List.of(
        new Sign("none","Без созвездия","Судьба ещё не связана со звёздами.","Без дополнительных бонусов."),
        new Sign("warrior","Воин","Покровитель решительных бойцов.","+10% урона в ближнем бою."),
        new Sign("mage","Маг","Знак терпеливого изучения тайных искусств.","+10% получаемого опыта. С Ars Nouveau: +15% восстановления маны."),
        new Sign("thief","Вор","Удача благоволит тем, кто действует осторожно.","+1 к удаче; на 10% меньше урона от падения."),
        new Sign("lady","Леди","Знак выдержки и крепкого здоровья.","+4 максимальных HP (два сердца)."),
        new Sign("steed","Конь","Покровитель путников, не терпящих промедления.","+10% скорости передвижения."),
        new Sign("lord","Лорд","Сила восстановления требует осторожности перед огнём.","Восстановление 1 HP каждые 8 секунд при сытости от 6. +25% урона от огня.")
    );
    public static List<Sign> all(){return ALL;}
    public static Sign get(String id){return ALL.stream().filter(s->s.id.equals(id)).findFirst().orElse(null);}
}
