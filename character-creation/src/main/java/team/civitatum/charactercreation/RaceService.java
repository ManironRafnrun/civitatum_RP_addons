package team.civitatum.charactercreation;

import java.util.*;
import java.security.SecureRandom;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.core.Holder;
import team.civitatum.charactercreation.power.PassiveAttributes;
import team.civitatum.charactercreation.power.Powers;

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
        if(!mode.race() && race(p)==null) {p.displayClientMessage(Civitatum.message("choose_race_first"),false);return;}
        Edit session=new Edit(RANDOM.nextLong(),mode,item,time(p)+20*1800);
        EDITS.put(p.getUUID(),session);
        int kind=mode==EditorMode.CREATE?1:mode==EditorMode.FULL?2:5;
        RaceNetwork.send(p,new RaceNetwork.Clientbound(kind,p.getUUID(),session.token,p.getData(Civitatum.STATE).json()));
    }
    public static void naked(ServerPlayer p,boolean value) {
        var state=p.getData(Civitatum.STATE);if(!state.character().selected)return;
        var c=state.character().copy();c.naked=value;p.setData(Civitatum.STATE,state.withCharacter(c));RaceNetwork.sync(p);
        p.displayClientMessage(Civitatum.message(value?"clothing_hidden":"clothing_shown"),true);
    }
    public static void cancel(ServerPlayer p, long token) {
        Edit edit = EDITS.get(p.getUUID());
        if (edit != null && edit.token == token) EDITS.remove(p.getUUID());
    }
    public static void reset(ServerPlayer p) {
        var previous=p.getData(Civitatum.STATE);
        EDITS.remove(p.getUUID());Powers.clear(p);
        // A new character: its sky may be rewritten again and it has no mark; the clocks of the powers go on.
        p.setData(Civitatum.STATE,new RaceState(new CharacterData(),previous.cooldownUntil(),previous.signCooldownUntil(),
            0,false,0,null));
        apply(p);p.refreshDisplayName();RaceNetwork.sync(p);
        p.sendSystemMessage(Civitatum.message("race_reset"));
    }
    public static void openWithShard(ServerPlayer p) {
        var item=p.getInventory().contains(new net.minecraft.world.item.ItemStack(Civitatum.MIRROR.get()))?Civitatum.MIRROR.get():Civitatum.SHARD.get();
        openCosmetic(p,item);
    }
    public static void openCosmetic(ServerPlayer p,net.minecraft.world.item.Item item) {
        if(!p.isCreative() && !p.getInventory().contains(new net.minecraft.world.item.ItemStack(item))) {
            p.displayClientMessage(Civitatum.message("needs_item"),true);return;
        }
        open(p,EditorMode.COSMETIC,item);
    }
    public static void save(ServerPlayer p, long token, String json) {
        Edit edit = EDITS.get(p.getUUID());
        if (edit == null || edit.token != token || edit.expires < time(p) || !p.isAlive()) {
            p.displayClientMessage(Civitatum.message("editor_expired"), false); return;
        }
        try {
            CharacterData c = RaceCatalog.validate(Civitatum.GSON.fromJson(json, CharacterData.class));
            c.selected = true;
            RaceState previous = p.getData(Civitatum.STATE);
            boolean lot = c.birthsign.equals(BirthsignCatalog.LOT);
            c.birthsign = SignChoice.resolve(previous.character(), c.birthsign, edit.mode, RANDOM.nextDouble());
            AppearanceRules.check(previous.character(),c,edit.mode);
            boolean changed = !DataJson.GSON.toJsonTree(previous.character()).equals(DataJson.GSON.toJsonTree(c));
            if (edit.item!=null && changed && !p.isCreative()) {
                int slot = -1;
                for (int i = 0; i < p.getInventory().getContainerSize(); i++)
                    if (p.getInventory().getItem(i).is(edit.item)) { slot = i; break; }
                if (slot < 0) throw new IllegalArgumentException(Civitatum.ERROR + "item_missing");
                p.getInventory().getItem(slot).shrink(1);
            }
            if (edit.mode==EditorMode.CREATE) Powers.clear(p);
            p.setData(Civitatum.STATE, previous.withCharacter(c));
            if (lot) p.sendSystemMessage(Civitatum.message("sign_drawn",
                Component.translatable(BirthsignCatalog.get(c.birthsign).key("name"))));
            EDITS.remove(p.getUUID());
            apply(p); enforceBoots(p); p.refreshDisplayName(); RaceNetwork.sync(p);
            RaceNetwork.send(p, new RaceNetwork.Clientbound(3, p.getUUID(), token, "message.civitatum.saved"));
        } catch (RuntimeException ex) {
            RaceNetwork.send(p, new RaceNetwork.Clientbound(4, p.getUUID(), token,
                ex.getMessage() == null ? Civitatum.ERROR + "invalid" : ex.getMessage()));
        }
    }
    public static void apply(ServerPlayer p) {
        RaceDefinition r = race(p);
        var state = p.getData(Civitatum.STATE);
        double scale = r == null ? 1 : state.character().height / 1.8;
        // Native SCALE supplies the race contribution once, including the physical dimensions.
        modifier(p, Attributes.SCALE, "scale", scale - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(p, Attributes.BLOCK_INTERACTION_RANGE, "block_reach", scale - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(p, Attributes.ENTITY_INTERACTION_RANGE, "entity_reach", scale - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        PassiveAttributes.apply(p);
        p.refreshDimensions();
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
    public static Component bootsMessage(net.minecraft.world.entity.player.Player p) {
        return Civitatum.message("hobbit".equals(p.getData(Civitatum.STATE).character().race) ? "boots.hobbit" : "boots.beast");
    }
    public static void enforceBoots(ServerPlayer p) {
        if (!forbidsBoots(p) || p.getItemBySlot(EquipmentSlot.FEET).isEmpty()) return;
        var boots = p.getItemBySlot(EquipmentSlot.FEET).copy();
        p.setItemSlot(EquipmentSlot.FEET, net.minecraft.world.item.ItemStack.EMPTY);
        if (!p.getInventory().add(boots)) p.drop(boots, false);
        p.displayClientMessage(bootsMessage(p), true);
    }
    public static void logout(ServerPlayer p) { EDITS.remove(p.getUUID()); Powers.logout(p); }
}
