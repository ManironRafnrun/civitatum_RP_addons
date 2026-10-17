package team.civitatum.charactercreation;

import java.util.*;
import java.security.SecureRandom;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.core.Holder;

public final class RaceService {
    private record Edit(long token, EditorMode mode, net.minecraft.world.item.Item item, long expires) {}
    private static final Map<UUID, Edit> EDITS = new HashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    public static long time(ServerPlayer p) { return p.server.overworld().getGameTime(); }
    public static RaceDefinition race(net.minecraft.world.entity.player.Player p) {
        var c = p.getData(Civitatum.STATE).character();
        return c.selected ? RaceCatalog.get(c.race) : null;
    }
    public static void open(ServerPlayer p,boolean choose,boolean item) {
        open(p,choose?EditorMode.CREATE:item?EditorMode.COSMETIC:EditorMode.FULL,item?Civitatum.SHARD.get():null);
    }
    private static void open(ServerPlayer p,EditorMode mode,net.minecraft.world.item.Item item) {
        if(!mode.race() && race(p)==null) {p.displayClientMessage(Component.literal("Сначала выберите расу: /civitatum race choose"),false);return;}
        Edit session=new Edit(RANDOM.nextLong(),mode,item,time(p)+20*1800);
        EDITS.put(p.getUUID(),session);
        int kind=mode==EditorMode.CREATE?1:mode==EditorMode.FULL?2:5;
        RaceNetwork.send(p,new RaceNetwork.Clientbound(kind,p.getUUID(),session.token,p.getData(Civitatum.STATE).json()));
    }
    public static void naked(ServerPlayer p,boolean value) {
        var state=p.getData(Civitatum.STATE);if(!state.character().selected)return;
        var c=state.character().copy();c.naked=value;p.setData(Civitatum.STATE,new RaceState(c,state.curseDeaths(),state.cooldownUntil()));RaceNetwork.sync(p);
        p.displayClientMessage(Component.literal(value?"Декоративная одежда скрыта":"Декоративная одежда показана"),true);
    }
    public static void cancel(ServerPlayer p, long token) {
        Edit edit = EDITS.get(p.getUUID());
        if (edit != null && edit.token == token) EDITS.remove(p.getUUID());
    }
    public static void reset(ServerPlayer p) {
        var previous=p.getData(Civitatum.STATE);
        EDITS.remove(p.getUUID());RaceAbilities.clearTransient(p);MetalEating.logout(p);
        p.setData(Civitatum.STATE,new RaceState(new CharacterData(),previous.curseDeaths(),previous.cooldownUntil()));
        apply(p);p.refreshDisplayName();RaceNetwork.sync(p);
        p.sendSystemMessage(Component.literal("Раса сброшена. Новый выбор: /civitatum race choose"));
    }
    public static void openWithShard(ServerPlayer p) {
        var item=p.getInventory().contains(new net.minecraft.world.item.ItemStack(Civitatum.MIRROR.get()))?Civitatum.MIRROR.get():Civitatum.SHARD.get();
        openCosmetic(p,item);
    }
    public static void openCosmetic(ServerPlayer p,net.minecraft.world.item.Item item) {
        if(!p.isCreative() && !p.getInventory().contains(new net.minecraft.world.item.ItemStack(item))) {
            p.displayClientMessage(Component.literal("Для изменения внешности нужно Зеркало преображения или Осколок Демитары"),true);return;
        }
        open(p,EditorMode.COSMETIC,item);
    }
    public static void save(ServerPlayer p, long token, String json) {
        Edit edit = EDITS.get(p.getUUID());
        if (edit == null || edit.token != token || edit.expires < time(p) || !p.isAlive()) {
            p.displayClientMessage(Component.literal("Редактор закрыт или устарел. Откройте его снова."), false); return;
        }
        try {
            CharacterData c = RaceCatalog.validate(Civitatum.GSON.fromJson(json, CharacterData.class));
            c.selected = true;
            RaceState previous = p.getData(Civitatum.STATE);
            AppearanceRules.check(previous.character(),c,edit.mode);
            boolean changed = !DataJson.GSON.toJsonTree(previous.character()).equals(DataJson.GSON.toJsonTree(c));
            if (edit.item!=null && changed && !p.isCreative()) {
                int slot = -1;
                for (int i = 0; i < p.getInventory().getContainerSize(); i++)
                    if (p.getInventory().getItem(i).is(edit.item)) { slot = i; break; }
                if (slot < 0) throw new IllegalArgumentException("Предмет для изменения внешности больше не находится в инвентаре");
                p.getInventory().getItem(slot).shrink(1);
            }
            if (edit.mode==EditorMode.CREATE) RaceAbilities.clearTransient(p);
            p.setData(Civitatum.STATE, new RaceState(c, previous.curseDeaths(), previous.cooldownUntil()));
            EDITS.remove(p.getUUID());
            apply(p); enforceBoots(p); p.refreshDisplayName(); RaceNetwork.sync(p);
            RaceNetwork.send(p, new RaceNetwork.Clientbound(3, p.getUUID(), token, "Персонаж сохранён"));
        } catch (RuntimeException ex) {
            RaceNetwork.send(p, new RaceNetwork.Clientbound(4, p.getUUID(), token,
                ex.getMessage() == null ? "Некорректные данные персонажа" : ex.getMessage()));
        }
    }
    public static void apply(ServerPlayer p) {
        RaceDefinition r = race(p);
        var state = p.getData(Civitatum.STATE);
        float hp = r == null ? 20 : switch (r.id) {
            case "dwarf","firbolg","orthosian" -> 24;
            case "orc" -> 22;
            case "goblin" -> 16;
            case "hobbit","astral_elf","dark_elf","night_elf","wood_elf" -> 18;
            case "jahar" -> Math.max(ModConfig.INSTANCE.jaharHealthFloor, 20 - 2 * state.curseDeaths());
            default -> 20;
        };
        modifier(p, Attributes.MAX_HEALTH, "health", hp - 20, AttributeModifier.Operation.ADD_VALUE);
        modifier(p, Attributes.ARMOR, "armor", r != null && (r.dragon() || r.id.equals("forged")) ? 2 : 0, AttributeModifier.Operation.ADD_VALUE);
        modifier(p, Attributes.KNOCKBACK_RESISTANCE, "knockback",
            r == null ? 0 : r.id.equals("forged") ? .30 : List.of("dwarf","orthosian").contains(r.id) ? .15 : 0, AttributeModifier.Operation.ADD_VALUE);
        double scale = r == null ? 1 : state.character().height / 1.8;
        // Native SCALE supplies the race contribution once, including the physical dimensions.
        modifier(p, Attributes.SCALE, "scale", scale - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(p, Attributes.BLOCK_INTERACTION_RANGE, "block_reach", scale - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(p, Attributes.ENTITY_INTERACTION_RANGE, "entity_reach", scale - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(p,Attributes.MAX_HEALTH,"birthsign_health",r!=null && state.character().birthsign.equals("lady")?4:0,AttributeModifier.Operation.ADD_VALUE);
        modifier(p,Attributes.LUCK,"birthsign_luck",r!=null && state.character().birthsign.equals("thief")?1:0,AttributeModifier.Operation.ADD_VALUE);
        updateSpeed(p);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        p.refreshDimensions();
    }
    public static void updateSpeed(ServerPlayer p) {
        RaceDefinition r = race(p);
        double speed = r == null ? 0 : switch(r.id) {
            case "dwarf" -> -.08;
            case "forged" -> -.05;
            case "goblin" -> .10;
            case "wood_elf" -> p.onGround() ? .08 : 0;
            case "dark_elf" -> p.level().getMaxLocalRawBrightness(p.blockPosition()) <= 7 ? .10 : 0;
            case "night_elf" -> p.level().dimensionType().hasSkyLight() && !p.level().dimensionType().hasFixedTime()
                && p.level().getDayTime() % 24000 >= 13000 && p.level().getDayTime() % 24000 <= 23000 ? .10 : 0;
            case "forest_algnor" -> isForest(p) ? .10 : 0;
            default -> 0;
        };
        modifier(p, Attributes.MOVEMENT_SPEED, "speed", speed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(p,Attributes.MOVEMENT_SPEED,"birthsign_speed",r!=null && p.getData(Civitatum.STATE).character().birthsign.equals("steed")?.10:0,AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }
    private static boolean isForest(ServerPlayer p) {
        String key = p.level().getBiome(p.blockPosition()).unwrapKey().map(k -> k.location().toString()).orElse("");
        return key.startsWith("minecraft:") && (key.contains("forest") || key.contains("taiga") || key.contains("grove"));
    }
    public static void modifier(ServerPlayer p, Holder<Attribute> attribute, String id, double amount, AttributeModifier.Operation operation) {
        var instance = p.getAttribute(attribute);
        if (instance == null) return;
        var key = Civitatum.res(id);
        var previous = instance.getModifier(key);
        if (previous != null && previous.amount() == amount && previous.operation() == operation) return;
        instance.removeModifier(key);
        if (amount != 0) instance.addTransientModifier(new AttributeModifier(key, amount, operation));
    }
    public static boolean forbidsBoots(net.minecraft.world.entity.player.Player p) {
        var r = race(p);
        return r != null && !r.bootsAllowed && !(r.algnor() && ModConfig.INSTANCE.algnorBootsAllowed);
    }
    public static String bootsMessage(net.minecraft.world.entity.player.Player p) {
        return "hobbit".equals(p.getData(Civitatum.STATE).character().race)
            ? "Ваши большие ноги не подходят для обуви" : "Звероподобные расы не способны надеть ботинки";
    }
    public static void enforceBoots(ServerPlayer p) {
        if (!forbidsBoots(p) || p.getItemBySlot(EquipmentSlot.FEET).isEmpty()) return;
        var boots = p.getItemBySlot(EquipmentSlot.FEET).copy();
        p.setItemSlot(EquipmentSlot.FEET, net.minecraft.world.item.ItemStack.EMPTY);
        if (!p.getInventory().add(boots)) p.drop(boots, false);
        p.displayClientMessage(Component.literal(bootsMessage(p)), true);
    }
    public static void logout(ServerPlayer p) { EDITS.remove(p.getUUID()); RaceAbilities.logout(p); MetalEating.logout(p); }
}
