package team.civitatum.hud.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.*;
import org.lwjgl.glfw.GLFW;
import team.civitatum.hud.mixin.SlotPositionAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.IdentityHashMap;
import java.util.regex.Pattern;

/** Vanilla InventoryMenu and AbstractContainerScreen retain all server-authoritative item operations. */
public final class CivitatumInventoryScreen extends AbstractContainerScreen<AbstractContainerMenu> {
    private static final int TEXT = 0xE9E3D4, MUTED = 0xABA799, GOLD = 0xD3B478;
    private static final int ARROW = 0xFFB29A6B;
    /** Vanilla slot pitch and item size. */
    private static final int SLOT = 18, ITEM = 16;
    /** Left edge of the screen title in the wide layout. */
    private static final int TITLE_X = 10;
    private static final int RESET_WIDTH = 66;
    /** Space a window leaves below itself by default; it may be moved or stretched closer to the edge. */
    private static final int DEFAULT_FREE_BELOW = 44;
    /** The hint under the windows takes this much height; a window moved over it hides it. */
    private static final int FOOTER_BOTTOM = 16;
    /** Main inventory: slots 9..35 of the player inventory; 0..8 are the hotbar, 36..39 armour, 40 the off hand. */
    private static final int HOTBAR = 9, MAIN = 27, MAIN_END = 36, ARMOR_END = 39, OFFHAND = 40;
    /** Menu slots of the vanilla player menu; Curios adds its own slots after them. */
    private static final int PLAYER_MENU_SLOTS = 46;
    /** Aether's accessories menu: menu slots 5..12 are its eight accessory slots. */
    private static final int AETHER_FIRST = 5, AETHER_END = 13;
    /** Category buttons and the tooltip keys {@code category.<n>}: all, weapons, armour, food, blocks, other. */
    private static final int CATEGORIES = 6;
    /** Wide layout needs this much width; below it the information and equipment windows share a place. */
    private static final int WIDE_WIDTH = 590;
    /** Windows shorter than this hide the category row and move the grid up. */
    private static final int CATEGORY_ROW_HEIGHT = 220;
    /** Z step between stacked windows; items are drawn up to 210 above their window. */
    private static final int WINDOW_Z = 40, OVERLAY_Z = 210, TOP_Z = 400;
    /** Between the item count and TrashSlot's cell, which stands under the last cell of the hotbar. */
    private static final int TRASH_GAP = 4;
    /** Sets TrashSlot's cell apart from the hotbar; the 7 px under the cell, 2 of them the window's frame, leave 3. */
    private static final int TRASH_TOP_GAP = 2;
    /**
     * Accessories' classic menu: a column of accessory slots with a cosmetic column on its left, eight rows in view.
     * The menu creates them at x = -46 (cosmetic) and -26, y = 8 + 18 * row, rows shifted by the scrolled index.
     */
    private static final int CLASSIC_ROWS = 8, CLASSIC_FIRST_Y = 8;
    /**
     * The accessories window keeps room for its title bar (above the rows) and the cosmetics button (below). It is
     * as wide as its columns: 8 px margins, 2 px between the columns, 6 px for the scroll bar.
     */
    private static final int ACCESSORIES_TOP = 24, ACCESSORIES_BOTTOM = 28, ACCESSORIES_MARGIN = 8, COLUMN_GAP = 2;
    private static final int SCROLL_BAR = 6, ACCESSORIES_MIN_WIDTH = 2 * ACCESSORIES_MARGIN + SLOT + SCROLL_BAR;
    private static final ItemStack EQUIPMENT_ICON = new ItemStack(Items.IRON_CHESTPLATE);
    /** Marks an accessory hidden on the body: a dark veil and a diagonal strike in muted red. */
    private static final int HIDDEN_SHADE = 0x8818130E, HIDDEN_STRIKE = 0xFFB04A3A;
    private static final ResourceLocation COSMETIC_ICON =
        ResourceLocation.fromNamespaceAndPath("accessories", "textures/gui/slot/cosmetic.png");
    /** The model sits between the armour column and the off-hand slot, this far from each window edge. */
    private static final int MODEL_LEFT = 32, MODEL_RIGHT = 32, MODEL_TOP = 25;
    /** Under the model: the experience bar, then the armour value, the level and the backpack button. */
    private static final int STATUS_HEIGHT = 30, XP_BAR_HEIGHT = 7, XP_COLOR = 0xFF80BF3A, LEVEL_COLOR = 0x80FF20;
    private static final ResourceLocation ARMOR_ICON = ResourceLocation.withDefaultNamespace("hud/armor_full");
    /** Hidden slots are parked far outside the screen. */
    private static final int HIDDEN = -10000;
    private static final EquipmentSlot[] ARMOR = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Pattern TRAILING_ZEROS = Pattern.compile("0+$"), TRAILING_POINT = Pattern.compile("\\.$");
    private final Player player;
    private final IdentityHashMap<Slot, int[]> original = new IdentityHashMap<>();
    private final CompatBridge.Kind kind;
    private InventoryPanel accessories, equipment, items, information, powers, dragging;
    private final List<InventoryPanel> panels = new ArrayList<>();
    private final IdentityHashMap<Slot, InventoryPanel> owners = new IdentityHashMap<>();
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final List<Button> categories = new ArrayList<>();
    /** Row of each accessory slot of the classic Accessories menu, counted from the top of its list. */
    private final IdentityHashMap<Slot, Integer> accessoryRows = new IdentityHashMap<>();
    private List<Slot> extraSlots = List.of();
    private Button cosmetics, powersButton;
    /** Scrolled rows of the backpack grid, and of the accessories window when its menu does not scroll itself. */
    private int extraRow, accessoryRow, accessoryMaxRow, accessoryRowCount;
    /** A worn accessory under the cursor where the window only shows them; its tooltip is drawn last. */
    private ItemStack wornHover = ItemStack.EMPTY;
    private boolean wornHoverShown;
    private EditBox search;
    private Button summaryTab, attributeTab, attributeFilter, ability, compactToggle, gearButton;
    private final PowersView powersView = new PowersView();
    private boolean autoBackpack = true, menuRequested;
    private int playerRow, resizeMask, anchorX, anchorY, anchorW, anchorH;
    private final WheelSteps wheel = new WheelSteps();
    /** Positions of the scrolled player and backpack grids, set where their slots are laid out. */
    private RowScrollBar playerScroll, extraScroll;
    private Slot trash;
    private boolean modifiedAttributes;
    private int category, page, scroll, dragOffsetX, dragOffsetY;
    private boolean compact, compactInfo;
    private String query = "";
    /** {@link #query} prepared for matching, updated together with it. */
    private String needle = "";

    public CivitatumInventoryScreen(Player player) {
        this(player, player.inventoryMenu);
    }

    public CivitatumInventoryScreen(Player player, AbstractContainerMenu menu) {
        super(menu, player.getInventory(), tr("title"));
        this.player = player;
        this.kind = CompatBridge.kind(menu);
        // Reopening over our own screen keeps the true menu coordinates, not the ones we moved the slots to.
        var previous = Minecraft.getInstance().screen instanceof CivitatumInventoryScreen other && other.menu == menu
            ? other : null;
        boolean classic = classicAccessories();
        int scrolled = classic ? CompatBridge.integer(menu, "scrolledIndex", 0) : 0;
        for (var slot : menu.slots) {
            boolean known = previous != null && previous.original.containsKey(slot);
            original.put(slot, known ? previous.original.get(slot).clone() : new int[]{slot.x, slot.y});
            if (classic && CompatBridge.accessoryItemSlot(slot)) {
                accessoryRows.put(slot, previous != null && previous.accessoryRows.containsKey(slot)
                    ? previous.accessoryRows.get(slot) : (slot.y - CLASSIC_FIRST_Y) / SLOT + scrolled);
            }
        }
    }

    /** Accessories' original menu, the classic column; it scrolls its own slots in and out of view. */
    private boolean classicAccessories() {
        return kind == CompatBridge.Kind.ACCESSORIES && CompatBridge.originalAccessories(menu);
    }

    public static Component tr(String key, Object... args) {
        return Component.translatable("gui.civitatum_hud." + key, args);
    }

    @Override
    protected void init() {
        // TrashSlot's parts, for the cell drawn in the items window
        TrashSlotBridge.register();
        super.init();
        leftPos = 0;
        topPos = 0;
        imageWidth = width;
        imageHeight = height;
        compact = width < WIDE_WIDTH;
        panels.clear();
        categories.clear();
        widgets.clear();
        createPanels();
        updateVisibility();
        createWidgets();
        positionControls();
        positionSlots();
    }

    private void createPanels() {
        boolean backpack = kind == CompatBridge.Kind.BACKPACK;
        // The accessories window docks on the left of the equipment window and shifts the whole group right.
        int aw = accessoriesShown() ? ACCESSORIES_MIN_WIDTH : 0;
        int start = compact ? 4 + aw : Math.max(6 + aw, (width - 584 - aw) / 2 + aw), y = compact ? 28 : 32;
        int h = Math.min(backpack || kind == CompatBridge.Kind.AETHER ? 302 : 236,
            height - InventoryPanel.TOP_BAR - DEFAULT_FREE_BELOW);
        int ew = compact ? Math.clamp(width - 212 - aw, 112, 142) : 146;
        int gridMinHeight = backpack ? 171 : 164;
        accessories = new InventoryPanel("accessories", start - aw, y, ACCESSORIES_MIN_WIDTH, h, ACCESSORIES_MIN_WIDTH,
            gridMinHeight);
        equipment = new InventoryPanel("equipment", start, y, ew, h, 112, gridMinHeight);
        items = new InventoryPanel("items", start + ew, y, compact ? Math.min(196, width - ew - 8 - aw) : 214, h, 186,
            gridMinHeight);
        // In the compact layout the character window takes the place of equipment and accessories together.
        information = new InventoryPanel("information", compact ? start - aw : items.x + items.width + 8, y,
            compact ? ew + aw : 208, Math.min(190, height - 54), 112, 128);
        information.height = Math.min(164, height - 70);
        powers = new InventoryPanel("powers", compact ? 4 : information.x, compact ? 48 : y + information.height + 8,
            compact ? width - 8 : 208, Math.min(180, height - 70), 160, 144);
        panels.add(accessories);
        panels.add(equipment);
        panels.add(items);
        panels.add(information);
        panels.add(powers);
        for (var panel : panels) {
            panel.restore(InventoryPreferences.get().panels.get(layoutKey(panel)), width, height);
            panel.constrain(width, height);
        }
    }

    private void createWidgets() {
        search = addWidget(new EditBox(font, 0, 0, 100, 18, tr("search")));
        search.setBordered(false);
        search.setTextColor(TEXT);
        search.setTextColorUneditable(MUTED);
        search.setMaxLength(80);
        search.setHint(tr("search"));
        search.setValue(query);
        search.setResponder(this::setQuery);
        for (int i = 0; i < CATEGORIES; i++) {
            final int c = i;
            categories.add(button(tr("category.short." + i), b -> category = c, 27));
        }
        boolean backpack = kind == CompatBridge.Kind.BACKPACK;
        gearButton = backpack
            ? new MorrowindIconButton(() -> EQUIPMENT_ICON, tr("equipment"),
                b -> InventoryClient.personalInventory(true))
            : new MorrowindIconButton(() -> CompatBridge.backpack(player), tr("storage.backpack"),
                b -> CompatBridge.openBackpack());
        addWidget(gearButton);
        widgets.add(gearButton);
        summaryTab = button(tr("statistics"), b -> {
            page = 0;
            scroll = 0;
        }, 70);
        attributeTab = button(tr("attributes.short"), b -> {
            page = 2;
            scroll = 0;
        }, 50);
        attributeFilter = button(tr("attributes.all"), b -> {
            modifiedAttributes = !modifiedAttributes;
            scroll = 0;
        }, 100);
        ability = button(tr("activate"), b -> {
            AbilitySelection.activate(player);
            onClose();
        }, 100);
        cosmetics = addWidget(new MorrowindIconButton(COSMETIC_ICON, tr("cosmetics"), this::cosmeticsShown,
            b -> toggleCosmetics()));
        widgets.add(cosmetics);
        var reset = button(tr("reset"), b -> resetLayout(), RESET_WIDTH);
        reset.setPosition(width - RESET_WIDTH - SidebarRow.RIGHT_MARGIN, 4);
        powersButton = button(tr("powers"), b -> togglePowers(), 54);
        compactToggle = compact ? button(tr(compactInfo ? "equipment" : "information"), b -> {
            compactInfo = !compactInfo;
            updateVisibility();
            positionControls();
            positionSlots();
            b.setMessage(tr(compactInfo ? "equipment" : "information"));
        }, 82) : null;
        if (compactToggle != null) compactToggle.setPosition(6, 4);
        // Top left: after "Character" in the narrow layout, after the screen title in the wide one.
        int powersX = compactToggle != null ? compactToggle.getX() + compactToggle.getWidth() + 4
            : TITLE_X + font.width(tr("title")) + 8;
        powersButton.setPosition(powersX, 4);
    }

    private void setQuery(String value) {
        query = value;
        needle = value.toLowerCase(Locale.ROOT).strip();
    }

    private void toggleCosmetics() {
        if (kind == CompatBridge.Kind.CURIOS) {
            CompatBridge.cosmetics(menu);
        } else if (classicAccessories()) {
            CompatBridge.accessoryCosmetics(menu);
        } else {
            var preferences = InventoryPreferences.get();
            preferences.accessoryCosmetics = !preferences.accessoryCosmetics;
            preferences.save();
        }
    }

    /** Whether the cosmetic column is shown; Curios keeps its own state, so its button never lights up. */
    private boolean cosmeticsShown() {
        if (kind == CompatBridge.Kind.CURIOS) return false;
        return classicAccessories() ? CompatBridge.accessoriesCosmetics(menu)
            : InventoryPreferences.get().accessoryCosmetics;
    }

    /**
     * One column of accessories; with cosmetics on, their cosmetic column and the cosmetic armour column on its left.
     * Curios lays its slots out in two columns.
     */
    private int accessoryColumns() {
        if (kind == CompatBridge.Kind.CURIOS) return 2;
        if (!cosmeticsShown()) return 1;
        return hasCosmeticArmor() ? 3 : 2;
    }

    /** Accessories' classic menu has no cosmetic armour slots; everywhere else they are shown or viewed. */
    private boolean hasCosmeticArmor() {
        return !classicAccessories();
    }

    /** Left edge of the main accessory column, the rightmost one; the cosmetic columns are on its left. */
    private int mainAccessoryX() {
        return accessories.x + ACCESSORIES_MARGIN + (accessoryColumns() - 1) * (accessoryPitch() + COLUMN_GAP);
    }

    /** Left edge of the accessories' cosmetic column, next to the main one. */
    private int cosmeticAccessoryX() {
        return mainAccessoryX() - accessoryPitch() - COLUMN_GAP;
    }

    private void togglePowers() {
        powers.visible = !powers.visible;
        InventoryPreferences.get().powersVisible = powers.visible;
        InventoryPreferences.get().save();
        if (powers.visible) raise(powers);
    }

    private Button button(Component label, Button.OnPress action, int w) {
        var b = addWidget(new MorrowindButton(label, action, w));
        widgets.add(b);
        return b;
    }

    private String layoutKey(InventoryPanel panel) {
        String layout = switch (kind) {
            case BACKPACK -> "joint.";
            case AETHER -> "aether.";
            default -> "";
        };
        return "v4." + (compact ? "compact." : "wide.") + layout + panel.id;
    }

    /**
     * The accessories window is shown while Accessories is installed and cannot be closed on its own; it belongs to the
     * equipment window and is hidden with it (the compact layout's Character / Equipment switch).
     */
    private static boolean accessoriesShown() {
        return CompatBridge.loaded("accessories");
    }

    private void updateVisibility() {
        equipment.visible = !compact || !compactInfo;
        accessories.visible = accessoriesShown() && equipment.visible;
        information.visible = !compact || compactInfo;
        powers.visible = InventoryPreferences.get().powersVisible && !compact;
        dockEquipment();
    }

    /** Equipment sticks to the left of the items window, and the accessories window to the left of equipment. */
    private void dockEquipment() {
        int cell = accessoryPitch(), columns = accessoryColumns();
        accessories.width = 2 * ACCESSORIES_MARGIN + columns * cell + (columns - 1) * COLUMN_GAP + SCROLL_BAR;
        int aw = accessories.visible ? accessories.width : 0;
        equipment.width = Math.max(equipment.minWidth, Math.min(equipment.width, width - items.width - 8 - aw));
        items.x = Math.max(4 + equipment.width + aw, Math.min(items.x, width - items.width - 4));
        equipment.x = items.x - equipment.width;
        equipment.y = items.y;
        equipment.height = items.height;
        accessories.x = equipment.x - accessories.width;
        accessories.y = items.y;
        accessories.height = items.height;
    }

    private void raise(InventoryPanel panel) {
        if (panel == accessories || panel == equipment || panel == items) {
            panels.remove(accessories);
            panels.remove(equipment);
            panels.remove(items);
            panels.add(accessories);
            panels.add(equipment);
            panels.add(items);
        } else {
            panels.remove(panel);
            panels.add(panel);
        }
    }

    private InventoryPanel topPane(double mx, double my) {
        for (int i = panels.size() - 1; i >= 0; i--) if (panels.get(i).contains(mx, my)) return panels.get(i);
        return null;
    }

    private boolean covered(InventoryPanel owner, int x, int y, int w, int h) {
        int index = panels.indexOf(owner);
        for (int i = index + 1; i < panels.size(); i++) {
            var p = panels.get(i);
            if (p.visible && x < p.x + p.width && x + w > p.x && y < p.y + p.height && y + h > p.y) return true;
        }
        return false;
    }

    private int accessoryPitch() { return SLOT; }
    public int slotPixels(Slot slot) { return ITEM; }
    private int pitch() { return SLOT; }
    private int columns() { return Math.clamp((items.width - 20) / pitch(), 1, 9); }
    private int hotbarPitch() { return SLOT; }
    private int hotbarX(int cell) {
        return items.x + (items.width - HOTBAR * hotbarPitch()) / 2 + cell * hotbarPitch();
    }
    private int hotbarTop() { return items.y + items.height - 24 - hotbarPitch(); }

    public boolean isSlotCovered(Slot slot) {
        return slot.x < 0 || covered(owners.get(slot), slot.x, slot.y, slotPixels(slot), slotPixels(slot));
    }

    private boolean slotUnder(Slot slot, InventoryPanel top, double mx, double my) {
        int size = slotPixels(slot);
        return !isSlotCovered(slot) && owners.get(slot) == top
            && mx >= slot.x && mx < slot.x + size && my >= slot.y && my < slot.y + size;
    }

    public Slot pickSlot(double mx, double my) {
        var top = topPane(mx, my);
        for (var slot : menu.slots) if (slot.isActive() && slotUnder(slot, top, mx, my)) return slot;
        return null;
    }

    @Override
    protected boolean isHovering(int x, int y, int w, int h, double mx, double my) {
        for (var slot : menu.slots) if (slot.x == x && slot.y == y) return slotUnder(slot, topPane(mx, my), mx, my);
        return super.isHovering(x, y, w, h, mx, my);
    }

    public void allowAutoBackpack(boolean value) { autoBackpack = value; }

    private void resetLayout() {
        var prefs = InventoryPreferences.get();
        for (var panel : panels) prefs.panels.remove(layoutKey(panel));
        prefs.save();
        clearWidgets();
        init();
    }

    private void positionControls() {
        boolean free = menu.getCarried().isEmpty();
        boolean wearsBackpack = !CompatBridge.backpack(player).isEmpty();
        // The accessories window follows its cosmetic column, so it is docked again on every frame.
        dockEquipment();
        search.setPosition(items.x + 12, items.y + 25);
        search.setWidth(items.width - 24);
        int buttonWidth = (items.width - 20) / CATEGORIES;
        for (int i = 0; i < CATEGORIES; i++) {
            categories.get(i).setPosition(items.x + 10 + i * buttonWidth, items.y + 45);
            categories.get(i).setWidth(buttonWidth - 1);
        }
        // Centred under the main column, not the window.
        cosmetics.setPosition(mainAccessoryX() + (accessoryPitch() - MorrowindIconButton.WIDTH) / 2,
            accessories.y + accessories.height - 24);
        cosmetics.visible = accessories.visible
            && (kind != CompatBridge.Kind.CURIOS || CompatBridge.flag(menu, "hasCosmetics"));
        cosmetics.active = free;
        if (powersButton != null) powersButton.active = free;
        int half = (information.width - 20) / 2;
        summaryTab.setPosition(information.x + 8, information.y + 25);
        summaryTab.setWidth(half);
        attributeTab.setPosition(information.x + 12 + half, information.y + 25);
        attributeTab.setWidth(half);
        attributeTab.visible = information.visible;
        attributeFilter.setPosition(information.x + 8, information.y + information.height - 27);
        attributeFilter.setWidth(information.width - 16);
        attributeFilter.visible = information.visible && page == 2;
        attributeFilter.setMessage(tr(modifiedAttributes ? "attributes.modified" : "attributes.all"));
        // Bottom right of the status row, under the experience bar.
        gearButton.setPosition(equipment.x + equipment.width - 9 - MorrowindIconButton.WIDTH, statusRowY());
        gearButton.visible = equipment.visible && CompatBridge.loaded("camping")
            && kind != CompatBridge.Kind.AETHER && (wearsBackpack || kind == CompatBridge.Kind.BACKPACK);
        gearButton.active = free;
        for (var b : categories) b.visible = items.height >= CATEGORY_ROW_HEIGHT;
        ability.setPosition(powers.x + 8, powers.y + powers.height - 23);
        ability.setWidth(powers.width - 16);
        summaryTab.visible = information.visible;
        var details = RacesBridge.read(player);
        ability.visible = powers.visible;
        var selected = AbilitySelection.selected(player);
        ability.active = selected != null && selected.ready().getAsBoolean();
        ability.setMessage(details != null && details.remainingTicks() > 0
            ? tr("remaining_short", (details.remainingTicks() + 19) / 20) : tr("activate"));
    }

    private void place(Slot slot, int x, int y) {
        ((SlotPositionAccessor) slot).civitatumHud$setX(x);
        ((SlotPositionAccessor) slot).civitatumHud$setY(y);
    }

    private void assign(Slot slot, InventoryPanel owner, int x, int y) {
        place(slot, x, y);
        owners.put(slot, owner);
    }

    private int gridTop() { return items.y + (items.height < CATEGORY_ROW_HEIGHT ? 51 : 71); }

    private int mainRows() {
        int space = hotbarTop() - gridTop() - 10;
        if (kind == CompatBridge.Kind.BACKPACK) space = (space - 20) / 2;
        return Math.max(1, Math.min((MAIN + columns() - 1) / columns(), space / pitch()));
    }

    private int bagTop() { return gridTop() + mainRows() * pitch() + SLOT; }

    private boolean isExtraSlot(Slot s, IdentityHashMap<Slot, Boolean> active) {
        return switch (kind) {
            // Cosmetic armour of the experimental menu is left out: the classic view has none.
            case ACCESSORIES -> CompatBridge.accessoryItemSlot(s) && active.getOrDefault(s, false);
            case BACKPACK -> s.container != player.getInventory();
            case AETHER -> s.index >= AETHER_FIRST && s.index < AETHER_END;
            default -> s.index >= PLAYER_MENU_SLOTS;
        };
    }

    private void positionSlots() {
        owners.clear();
        // Activity is read at the original coordinates: some mods derive it from the slot position.
        var active = new IdentityHashMap<Slot, Boolean>();
        for (var slot : menu.slots) {
            int[] xy = original.computeIfAbsent(slot, s -> new int[]{s.x, s.y});
            place(slot, xy[0], xy[1]);
            active.put(slot, slot.isActive());
        }
        extraSlots = menu.slots.stream().filter(s -> isExtraSlot(s, active)).toList();
        for (var slot : menu.slots) place(slot, HIDDEN, HIDDEN);
        placePlayerSlots();
        if (kind == CompatBridge.Kind.ACCESSORIES && equipment.visible) {
            for (var slot : menu.slots) {
                int armor = CompatBridge.accessoryArmor(slot);
                if (armor >= 0) assign(slot, equipment, equipment.x + 10, armorY(armor));
            }
        }
        if (kind == CompatBridge.Kind.AETHER && equipment.visible) {
            for (int i = 0; i < extraSlots.size(); i++) {
                assign(extraSlots.get(i), equipment, equipment.x + 14 + i % 4 * SLOT,
                    equipment.y + equipment.height - 109 + i / 4 * SLOT);
            }
        } else if (kind == CompatBridge.Kind.BACKPACK) {
            placeExtraGrid();
        } else if (accessories.visible && (kind == CompatBridge.Kind.ACCESSORIES || kind == CompatBridge.Kind.CURIOS)) {
            if (classicAccessories()) placeClassicAccessories();
            else if (kind == CompatBridge.Kind.ACCESSORIES) {
                placeAccessoryColumn();
                if (cosmeticsShown()) placeCosmeticArmor();
            }
            else placeAccessoryGrid();
        }
        if (equipment.visible && hasCraftGrid()) {
            int cy = equipment.y + equipment.height - 53;
            for (int i = 1; i < 5; i++) {
                assign(menu.slots.get(i), equipment, equipment.x + 14 + (i - 1) % 2 * SLOT, cy + (i - 1) / 2 * SLOT);
            }
            assign(menu.slots.getFirst(), equipment, equipment.x + equipment.width - 31, cy + 9);
        }
    }

    private void placePlayerSlots() {
        int cols = columns(), cell = pitch(), gx = items.x + (items.width - cols * cell) / 2, rows = mainRows();
        int totalRows = (MAIN + cols - 1) / cols;
        playerRow = Math.clamp(playerRow, 0, Math.max(0, totalRows - rows));
        playerScroll = new RowScrollBar(gx + cols * cell + 2, gridTop(), rows * cell, playerRow, rows, totalRows);
        for (var slot : menu.slots) {
            if (slot.container != player.getInventory()) continue;
            int i = slot.getContainerSlot(), r = (i - HOTBAR) / cols - playerRow;
            // Row by row, as in vanilla screens: an item stays in the same order wherever it is seen.
            if (i >= HOTBAR && i < MAIN_END && r >= 0 && r < rows) {
                assign(slot, items, gx + (i - HOTBAR) % cols * cell, gridTop() + r * cell);
            } else if (i >= 0 && i < HOTBAR) {
                assign(slot, items, hotbarX(i), hotbarTop());
            } else if (i >= MAIN_END && i <= ARMOR_END && equipment.visible) {
                assign(slot, equipment, equipment.x + 10, armorY(ARMOR_END - i));
            } else if (i == OFFHAND && equipment.visible) {
                // At the right edge of the model, level with the boots.
                assign(slot, equipment, equipment.x + equipment.width - 26, armorY(ARMOR.length - 1));
            }
        }
    }

    /** Accessories' own column: rows as the menu scrolled them, the cosmetic column on the left. */
    private void placeClassicAccessories() {
        int cell = accessoryPitch(), shown = CompatBridge.integer(menu, "scrolledIndex", 0);
        int main = mainAccessoryX(), left = cosmeticAccessoryX(), top = accessories.y + ACCESSORIES_TOP;
        for (var slot : extraSlots) {
            Integer row = accessoryRows.get(slot);
            if (row == null || row - shown < 0 || row - shown >= CLASSIC_ROWS) continue;
            assign(slot, accessories, CompatBridge.cosmeticSlot(slot) ? left : main, top + (row - shown) * cell);
        }
    }

    /**
     * Accessories' experimental menu laid out as the classic column: eight rows, the cosmetic column on the left while
     * it is switched on, scrolled by this screen. All its slots are always active.
     */
    private void placeAccessoryColumn() {
        int cell = accessoryPitch();
        int main = mainAccessoryX(), left = cosmeticAccessoryX(), top = accessories.y + ACCESSORIES_TOP;
        // The menu adds every accessory as a pair: its cosmetic slot, then the slot itself.
        var rows = new ArrayList<Slot[]>();
        Slot cosmetic = null;
        for (var slot : extraSlots) {
            if (CompatBridge.cosmeticSlot(slot)) {
                cosmetic = slot;
                continue;
            }
            rows.add(new Slot[]{cosmetic, slot});
            cosmetic = null;
        }
        accessoryRowCount = rows.size();
        accessoryMaxRow = Math.max(0, rows.size() - CLASSIC_ROWS);
        accessoryRow = Math.clamp(accessoryRow, 0, accessoryMaxRow);
        boolean cosmetics = InventoryPreferences.get().accessoryCosmetics;
        for (int r = 0; r < CLASSIC_ROWS && accessoryRow + r < rows.size(); r++) {
            var row = rows.get(accessoryRow + r);
            int y = top + r * cell;
            if (cosmetics && row[0] != null) assign(row[0], accessories, left, y);
            assign(row[1], accessories, main, y);
        }
    }

    /**
     * The experimental menu's cosmetic armour slots, head to feet, in the leftmost column. They do not scroll with the
     * accessory rows: each sits beside the same rows as the armour in the equipment window.
     */
    private void placeCosmeticArmor() {
        int cell = accessoryPitch(), left = accessories.x + ACCESSORIES_MARGIN, top = accessories.y + ACCESSORIES_TOP;
        for (var slot : menu.slots) {
            int row = CompatBridge.cosmeticArmor(slot);
            if (row >= 0 && slot.isActive()) assign(slot, accessories, left, top + row * cell);
        }
    }

    /** Curios: its slots in two columns, scrolled by this screen. */
    private void placeAccessoryGrid() {
        int cell = accessoryPitch();
        int left = accessories.x + ACCESSORIES_MARGIN, top = accessories.y + ACCESSORIES_TOP;
        int visible = Math.max(1, (accessories.height - ACCESSORIES_TOP - ACCESSORIES_BOTTOM) / cell);
        accessoryMaxRow = Math.max(0, (extraSlots.size() + 1) / 2 - visible);
        accessoryRow = Math.clamp(accessoryRow, 0, accessoryMaxRow);
        for (int i = 0; i < extraSlots.size(); i++) {
            int r = i / 2 - accessoryRow;
            if (r >= 0 && r < visible) {
                assign(extraSlots.get(i), accessories, left + i % 2 * (cell + COLUMN_GAP), top + r * cell);
            }
        }
    }

    private void placeExtraGrid() {
        int cell = pitch();
        int columns = Math.min(columns(), extraSlots.size() == 24 ? 8 : 9);
        int top = bagTop();
        int visible = Math.max(1, (hotbarTop() - 10 - top) / cell);
        int total = Math.max(1, (extraSlots.size() + columns - 1) / columns);
        extraRow = Math.clamp(extraRow, 0, Math.max(0, total - visible));
        int left = items.x + (items.width - columns * cell) / 2;
        extraScroll = new RowScrollBar(left + columns * cell + 2, top, visible * cell, extraRow, visible, total);
        for (int i = 0; i < extraSlots.size(); i++) {
            int r = i / columns - extraRow;
            if (r >= 0 && r < visible) {
                assign(extraSlots.get(i), items, left + i % columns * cell, top + r * cell);
            }
        }
    }

    @Override
    public void containerTick() {
        CompatBridge.tick(menu);
        positionSlots();
        if (menuRequested || kind != CompatBridge.Kind.INVENTORY || !menu.getCarried().isEmpty()) return;
        // The accessory slots exist only in Accessories' menu, so the personal inventory always switches to it.
        // A worn backpack is then opened with its button instead of automatically.
        if (accessoriesShown()) menuRequested = CompatBridge.openAccessories();
        else if (autoBackpack && !CompatBridge.backpack(player).isEmpty()) menuRequested = CompatBridge.openBackpack();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float tick) {
        positionControls();
        positionSlots();
        placeTrash();
        super.render(g, mx, my, tick);
        renderTooltip(g, mx, my);
        if (trash != null && trashUnder(mx, my) && !menu.getCarried().isEmpty()) {
            g.renderTooltip(font, TrashSlotBridge.carriedTooltip(), mx, my);
        }
        var top = topPane(mx, my);
        if (page == 2 && top == information) AttributeView.tooltip(g, font, information);
        if (top == powers) powersView.tooltip(g, font, powers);
        var status = top == equipment ? statusTooltip(mx, my) : null;
        if (status != null) g.renderTooltip(font, status, mx, my);
        if (top == accessories && !wornHover.isEmpty()) {
            var lines = new ArrayList<>(getTooltipFromItem(minecraft, wornHover));
            if (!wornHoverShown) lines.add(tr("hidden_on_body").copy().withStyle(ChatFormatting.GRAY));
            lines.add(tr("accessories_elsewhere").copy().withStyle(ChatFormatting.GRAY));
            g.renderComponentTooltip(font, lines, mx, my);
        }
        if (hoveredSlot != null && hoveredSlot.getItem().isEmpty()) {
            var label = CompatBridge.slotLabel(hoveredSlot);
            if (label != null) g.renderTooltip(font, label.copy().append(" · ").append(tr("render_toggle")), mx, my);
        }
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).isHovered()) g.renderTooltip(font, tr("category." + i), mx, my);
        }
        for (var panel : panels) {
            if (panel == top && panel != accessories && panel.resizeEdges(mx, my) != 0) {
                g.renderTooltip(font, tr("resize_hint"), mx, my);
            }
        }
        if (hoveredSlot == null && dragging == null) {
            for (var panel : panels) if (panel.titleContains(mx, my)) g.renderTooltip(font, tr("move_hint"), mx, my);
        }
    }

    /** The item tooltip of an accessory slot says when the item is not drawn on the body. */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        var lines = super.getTooltipFromContainerItem(stack);
        if (hoveredSlot == null || CompatBridge.accessoryShown(hoveredSlot)) return lines;
        lines = new ArrayList<>(lines);
        lines.add(tr("hidden_on_body").copy().withStyle(ChatFormatting.GRAY));
        return lines;
    }

    /** Drawn inside its owner window, with that window's depth. */
    @Override
    protected void renderSlot(GuiGraphics g, Slot slot) {}

    /**
     * TrashSlot's slot in its cell, or null when it has none here: TrashSlot missing on either side, the cell hidden
     * with its key, the items window closed or the corner under another window.
     */
    private void placeTrash() {
        trash = TrashSlotBridge.slot();
        // under the hotbar's last cell, a little lower than the next row of a grid would be
        int x = hotbarX(HOTBAR - 1), y = hotbarTop() + SLOT + TRASH_TOP_GAP;
        if (trash != null && (!TrashSlotBridge.shown() || !items.visible
            || covered(items, x - 1, y - 1, ITEM + 2, ITEM + 2))) {
            // off the screen, as our hidden slots are: TrashSlot would still take a click at its last place
            place(trash, HIDDEN, HIDDEN);
            trash = null;
        }
        if (trash == null) {
            TrashCell.hide();
            return;
        }
        // TrashSlot reads the slot's place for its clicks and keys: the screen's corner is the origin here
        place(trash, x, y);
        TrashCell.show(x - 1, y - 1, ITEM + 2);
    }

    private boolean trashUnder(double mx, double my) {
        return TrashCell.inside((int) Math.floor(mx), (int) Math.floor(my)) && topPane(mx, my) == items;
    }

    /** The trash cell as our slots are: framed, its can drawn as the empty slots' hints, gold under the cursor. */
    private void renderTrash(GuiGraphics g, int mx, int my) {
        if (trash == null) return;
        renderSlotFrame(g, trash.x - 1, trash.y - 1, ITEM + 2);
        g.pose().pushPose();
        g.pose().translate(0, 0, -95);
        super.renderSlot(g, trash);
        g.flush();
        g.pose().popPose();
        if (!trashUnder(mx, my)) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, OVERLAY_Z);
        g.fill(trash.x, trash.y, trash.x + ITEM, trash.y + ITEM, MorrowindSkin.SLOT_HOVER);
        g.flush();
        g.pose().popPose();
    }

    @Override
    protected void renderSlotHighlight(GuiGraphics g, Slot slot, int mx, int my, float tick) {
        if (isSlotCovered(slot)) return;
        int size = slotPixels(slot);
        g.pose().pushPose();
        g.pose().translate(0, 0, panels.indexOf(owners.get(slot)) * WINDOW_Z + OVERLAY_Z);
        g.fill(slot.x, slot.y, slot.x + size, slot.y + size, MorrowindSkin.SLOT_HOVER);
        g.flush();
        g.pose().popPose();
    }

    public boolean highlighted(Slot slot) {
        return owners.get(slot) == items && !slot.getItem().isEmpty() && (!query.isBlank() || category != 0)
            && matches(slot.getItem());
    }

    /** An accessory switched off on the body (Ctrl+RMB): the item dimmed and struck through, as in Accessories. */
    private static void renderHidden(GuiGraphics g, int x, int y, int size) {
        GuiBatch.fills(g, () -> {
            g.fill(x, y, x + size, y + size, HIDDEN_SHADE);
            for (int i = 1; i < size - 1; i++) g.fill(x + i, y + i, x + i + 1, y + i + 1, HIDDEN_STRIKE);
        });
    }

    private void renderOwnedSlot(GuiGraphics g, Slot slot) {
        int size = slotPixels(slot);
        renderSlotFrame(g, slot.x - 1, slot.y - 1, size + 2);
        g.pose().pushPose();
        g.pose().translate(0, 0, -95);
        super.renderSlot(g, slot);
        g.flush();
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(0, 0, OVERLAY_Z);
        if (owners.get(slot) == items && !slot.getItem().isEmpty()) {
            if (!matches(slot.getItem())) {
                g.fill(slot.x, slot.y, slot.x + size, slot.y + size, 0xA518130E);
            } else if (highlighted(slot)) {
                MorrowindSkin.frame(g, slot.x - 2, slot.y - 2, size + 4, size + 4, true);
                g.fill(slot.x, slot.y + size - 2, slot.x + size, slot.y + size, 0xFFB5D78B);
            }
        }
        if (owners.get(slot) == accessories && !CompatBridge.accessoryShown(slot)) {
            renderHidden(g, slot.x, slot.y, size);
        }
        g.flush();
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float tick) {
        renderBg(g, tick, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float tick, int mx, int my) {
        if (!compact) g.drawString(font, tr("title"), TITLE_X, 9, GOLD, false);
        for (var panel : List.copyOf(panels)) {
            if (!panel.visible) continue;
            g.pose().pushPose();
            g.pose().translate(0, 0, panels.indexOf(panel) * WINDOW_Z);
            // The accessories window has no title text: it is narrower than any translation of it.
            Component label = panel == accessories ? Component.empty() : tr(panel.id);
            renderPanel(g, panel, label);
            if (panel == accessories) renderAccessories(g, mx, my);
            if (panel == equipment) renderEquipment(g);
            if (panel == items) renderItemsHeader(g);
            if (panel == information) {
                if (page == 2) {
                    scroll = AttributeView.render(g, font, player, information, scroll, modifiedAttributes, mx, my);
                } else {
                    renderInformation(g);
                }
            }
            if (panel == powers) powersView.render(g, font, powers, player, mx, my);
            for (var slot : menu.slots) if (owners.get(slot) == panel && !isSlotCovered(slot)) renderOwnedSlot(g, slot);
            if (panel == items) renderTrash(g, mx, my);
            if (panel == items && !covered(items, search)) search.render(g, mx, my, tick);
            for (var b : widgets) {
                if (widgetOwner(b) == panel && b.visible && !covered(panel, b)) b.render(g, mx, my, tick);
            }
            g.flush();
            g.pose().popPose();
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, TOP_Z);
        for (var b : widgets) if (widgetOwner(b) == null && b.visible) b.render(g, mx, my, tick);
        if (panels.stream().noneMatch(p -> p.visible && p.y + p.height > height - FOOTER_BOTTOM)) {
            String footer = tr(compact ? "footer_short" : "footer").getString();
            footer = font.plainSubstrByWidth(footer, Math.max(80, width - 176));
            g.drawString(font, footer, 8, height - 14, MUTED, false);
        }
        g.flush();
        g.pose().popPose();
    }

    private void renderEquipment(GuiGraphics g) {
        int px = equipment.x + MODEL_LEFT, py = equipment.y + MODEL_TOP, ph = modelHeight();
        int pw = equipment.width - MODEL_LEFT - MODEL_RIGHT;
        g.fill(px, py, px + pw, py + ph, 0x3018130E);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, px + 1, py + 1, px + pw - 1, py + ph - 1, modelScale(),
            0.0625F, px + pw / 2F + 8, py + ph / 2F + 6, player);
        renderStatus(g);
        if (hasCraftGrid()) {
            g.drawString(font, tr("crafting"), equipment.x + 9, equipment.y + equipment.height - 67, MUTED, false);
            var out = menu.slots.getFirst();
            int ax = out.x - 26, ay = out.y + 6;
            g.fill(ax, ay, ax + 17, ay + 5, ARROW);
            for (int n = 0; n < 4; n++) g.fill(ax + 17 + n, ay - 2 + n, ax + 18 + n, ay + 7 - n, ARROW);
            MorrowindSkin.frame(g, out.x - 3, out.y - 3, 22, 22, true);
        }
        if (kind == CompatBridge.Kind.AETHER) {
            g.drawString(font, "Aether", equipment.x + 9, equipment.y + equipment.height - 123, GOLD, false);
        }
        if (kind == CompatBridge.Kind.BACKPACK) {
            // The armour slots are not part of this menu; their items are shown for reference only.
            for (int i = 0; i < ARMOR.length; i++) {
                int x = equipment.x + 10, y = armorY(i);
                renderSlotFrame(g, x - 1, y - 1, SLOT);
                g.renderItem(player.getItemBySlot(ARMOR[i]), x, y);
            }
        }
    }

    /** The experience bar and armour value, hidden from the HUD and shown here instead. */
    private void renderStatus(GuiGraphics g) {
        int x = equipment.x + 9, y = xpBarY(), w = equipment.width - 18;
        g.fill(x, y, x + w, y + XP_BAR_HEIGHT, 0xCC17130E);
        MorrowindSkin.frame(g, x, y, w, XP_BAR_HEIGHT, false);
        g.fill(x + 2, y + 2, x + 2 + (int) ((w - 4) * Math.clamp(player.experienceProgress, 0, 1)), y + 5, XP_COLOR);
        int row = statusRowY() + 5;
        g.blitSprite(ARMOR_ICON, x, row - 1, 9, 9);
        g.drawString(font, String.valueOf(player.getArmorValue()), x + 11, row, TEXT, true);
        String level = String.valueOf(player.experienceLevel);
        g.drawString(font, level, equipment.x + (equipment.width - font.width(level)) / 2, row, LEVEL_COLOR, true);
    }

    private int modelHeight() {
        return equipment.height - (kind == CompatBridge.Kind.AETHER ? 144 : 94) - STATUS_HEIGHT;
    }

    private int xpBarY() {
        return equipment.y + MODEL_TOP + modelHeight() + 4;
    }

    /** Item position of an armour slot, head (0) to feet (3), within the model's height, see {@link ArmorColumn}. */
    private int armorY(int row) {
        int top = equipment.y + MODEL_TOP + 1, bottom = equipment.y + MODEL_TOP + modelHeight() - SLOT + 1;
        return top + ArmorColumn.offset(row, bottom - top);
    }

    private int statusRowY() {
        return xpBarY() + XP_BAR_HEIGHT + 2;
    }

    private Component statusTooltip(int mx, int my) {
        if (!equipment.visible || mx < equipment.x + 9 || mx >= equipment.x + equipment.width - 9) return null;
        int bar = xpBarY(), row = statusRowY();
        if (my >= bar && my < bar + XP_BAR_HEIGHT || my >= row && my < row + 18 && Math.abs(mx - equipment.x
                - equipment.width / 2) < 12) {
            return tr("experience", player.experienceLevel,
                (int) (player.experienceProgress * player.getXpNeededForNextLevel()), player.getXpNeededForNextLevel());
        }
        if (my >= row && my < row + 18 && mx < equipment.x + 40) {
            return tr("armor_status", player.getArmorValue(),
                number(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS)));
        }
        return null;
    }

    private void renderAccessories(GuiGraphics g, int mx, int my) {
        wornHover = ItemStack.EMPTY;
        int total, first, shown;
        if (kind == CompatBridge.Kind.INVENTORY) {
            return;
        } else if (kind != CompatBridge.Kind.ACCESSORIES && kind != CompatBridge.Kind.CURIOS) {
            renderWornAccessories(g, mx, my);
            total = accessoryRowCount;
            first = accessoryRow;
            shown = CLASSIC_ROWS;
        } else if (classicAccessories()) {
            total = CompatBridge.integer(menu, "totalSlots", 0);
            first = CompatBridge.integer(menu, "scrolledIndex", 0);
            shown = CLASSIC_ROWS;
        } else if (kind == CompatBridge.Kind.ACCESSORIES) {
            total = accessoryRowCount;
            first = accessoryRow;
            shown = CLASSIC_ROWS;
        } else {
            total = accessoryMaxRow + 1;
            first = accessoryRow;
            shown = 1;
        }
        if (total <= shown) return;
        // Scroll bar along the right edge.
        int x = accessories.x + accessories.width - 7, top = accessories.y + ACCESSORIES_TOP;
        int track = accessories.height - ACCESSORIES_TOP - ACCESSORIES_BOTTOM;
        int thumb = Math.max(8, track * shown / total), ty = top + (track - thumb) * first / Math.max(1, total - shown);
        g.fill(x, top, x + 2, top + track, 0xFF232829);
        g.fill(x, ty, x + 2, ty + thumb, 0xFFAF9462);
    }

    /**
     * Menus without accessory slots (a backpack, a chest) show what is worn, like the armour beside them: to look at,
     * not to change. The same column layout and the same cosmetic switch as the real slots.
     */
    private void renderWornAccessories(GuiGraphics g, int mx, int my) {
        var worn = CompatBridge.wornAccessories(player);
        var rows = worn.stream().filter(w -> w.armor() < 0).toList();
        accessoryRowCount = rows.size();
        accessoryMaxRow = Math.max(0, rows.size() - CLASSIC_ROWS);
        accessoryRow = Math.clamp(accessoryRow, 0, accessoryMaxRow);
        int cell = accessoryPitch(), size = ITEM;
        int main = mainAccessoryX(), looks = cosmeticAccessoryX(), top = accessories.y + ACCESSORIES_TOP;
        boolean cosmetics = cosmeticsShown(), here = topPane(mx, my) == accessories;
        for (int r = 0; r < CLASSIC_ROWS && accessoryRow + r < rows.size(); r++) {
            var accessory = rows.get(accessoryRow + r);
            int y = top + r * cell;
            if (cosmetics) renderWorn(g, accessory.look(), accessory.shown(), looks, y, size, here, mx, my);
            renderWorn(g, accessory.item(), accessory.shown(), main, y, size, here, mx, my);
        }
        if (!cosmetics) return;
        // Cosmetic armour, head to feet, in the leftmost column.
        for (var armor : worn) {
            if (armor.armor() < 0) continue;
            renderWorn(g, armor.look(), armor.shown(), accessories.x + ACCESSORIES_MARGIN, top + armor.armor() * cell,
                size, here, mx, my);
        }
    }

    private void renderWorn(GuiGraphics g, ItemStack stack, boolean shown, int x, int y, int size, boolean here, int mx,
                            int my) {
        renderSlotFrame(g, x - 1, y - 1, size + 2);
        if (!stack.isEmpty()) {
            float scale = size / (float) ITEM;
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(scale, scale, 1);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
        }
        // Also on an empty armour slot: hiding it hides the armour piece itself.
        if (!shown) {
            g.pose().pushPose();
            g.pose().translate(0, 0, OVERLAY_Z);
            renderHidden(g, x, y, size);
            g.pose().popPose();
        }
        if (!stack.isEmpty() && here && mx >= x && mx < x + size && my >= y && my < y + size) {
            wornHover = stack;
            wornHoverShown = shown;
        }
    }

    private void renderItemsHeader(GuiGraphics g) {
        g.fill(items.x + 8, items.y + 21, items.x + items.width - 8, items.y + 40, 0x35130E09);
        MorrowindSkin.frame(g, items.x + 8, items.y + 21, items.width - 16, 19, false);
        if (kind == CompatBridge.Kind.BACKPACK) {
            g.drawString(font, tr("storage.backpack"), items.x + 10, bagTop() - 12, GOLD, false);
            if (extraScroll != null) extraScroll.render(g);
        }
        if (playerScroll != null) playerScroll.render(g);
        int occupied = 0, matched = 0;
        for (var slot : menu.slots) {
            if (owners.get(slot) == items && !slot.getItem().isEmpty()) {
                occupied++;
                if (matches(slot.getItem())) matched++;
            }
        }
        int room = trash != null ? trash.x - 1 - TRASH_GAP - (items.x + 10) : items.width - 20;
        String count = font.plainSubstrByWidth(tr("items_count", matched, occupied).getString(), room);
        g.drawString(font, count, items.x + 10, items.y + items.height - 16, MUTED, false);
    }

    private InventoryPanel widgetOwner(AbstractWidget b) {
        if (categories.contains(b)) return items;
        if (b == cosmetics) return accessories;
        if (b == summaryTab || b == attributeTab || b == attributeFilter) return information;
        if (b == ability) return powers;
        if (b == gearButton) return equipment;
        return null;
    }

    private void renderPanel(GuiGraphics g, InventoryPanel p, Component label) {
        MorrowindSkin.body(g, p.x + 2, p.y + 2, p.width - 4, p.height - 4);
        MorrowindSkin.frame(g, p.x, p.y, p.width, p.height, false);
        g.fill(p.x + 2, p.y + 17, p.x + p.width - 2, p.y + 18, 0xFFAF9462);
        g.drawString(font, font.plainSubstrByWidth(label.getString(), p.width - 22), p.x + 8, p.y + 5, TEXT, true);
        for (int i = 0; i < 3; i++) {
            int right = p.x + p.width, bottom = p.y + p.height;
            g.fill(right - 4 - i * 3, bottom - 4, right - 3, bottom - 3 - i * 3, 0xFF958E78);
        }
    }

    private void renderSlotFrame(GuiGraphics g, int x, int y, int size) {
        GuiBatch.fills(g, () -> {
            g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0x3018100A);
            g.fill(x, y, x + size, y + 1, 0xFF473B2A);
            g.fill(x, y, x + 1, y + size, 0xFF473B2A);
            g.fill(x + 1, y + size - 1, x + size, y + size, 0xFFA48D61);
            g.fill(x + size - 1, y + 1, x + size, y + size, 0xFFA48D61);
        });
    }

    public int modelScale() {
        int pw = equipment.width - MODEL_LEFT - MODEL_RIGHT - 2, ph = modelHeight() - 2;
        double byHeight = (ph - 16) / Math.max(2.15, player.getBbHeight() + 0.35), byWidth = (pw - 16) / 1.55;
        return Math.max(8, (int) Math.floor(Math.min(byHeight, byWidth)));
    }

    private boolean hasCraftGrid() {
        return menu.slots.size() >= 5 && menu.slots.getFirst() instanceof ResultSlot;
    }

    private void renderInformation(GuiGraphics g) {
        var details = RacesBridge.read(player);
        int x = information.x + 9, y = information.y + 51, w = information.width - 18;
        int bottom = information.y + information.height - (page == 1 && details != null ? 34 : 14);
        List<Component> lines = page == 0 ? summary(details) : raceLines(details);
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (var line : lines) {
            wrapped.addAll(font.split(line, w - 4));
            if (page == 1) wrapped.add(FormattedCharSequence.EMPTY);
        }
        int lineHeight = 12, maxScroll = Math.max(0, wrapped.size() * lineHeight - (bottom - y));
        scroll = Math.clamp(scroll, 0, maxScroll);
        g.enableScissor(x, y, x + w, bottom);
        for (int i = 0; i < wrapped.size(); i++) {
            g.drawString(font, wrapped.get(i), x, y + i * lineHeight - scroll, TEXT, true);
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int track = bottom - y, thumb = Math.max(12, track * track / (track + maxScroll));
            int ty = y + (track - thumb) * scroll / maxScroll;
            int right = information.x + information.width;
            g.fill(right - 5, y, right - 3, bottom, 0xFF232829);
            g.fill(right - 5, ty, right - 3, ty + thumb, 0xFFAF9462);
        }
    }

    private List<Component> summary(RacesBridge.Details details) {
        List<Component> lines = new ArrayList<>();
        String name = details != null && !details.name().isBlank() ? details.name() : player.getName().getString();
        lines.add(Component.literal(name).withColor(GOLD));
        if (details != null) lines.add(tr("race", details.race()));
        lines.add(tr("health", number(player.getHealth()), number(player.getMaxHealth())));
        lines.add(tr("food", player.getFoodData().getFoodLevel()));
        lines.add(tr("level", player.experienceLevel));
        lines.add(tr("armor", player.getArmorValue()));
        lines.add(tr("toughness", number(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS))));
        lines.add(tr("damage", number(player.getAttributeValue(Attributes.ATTACK_DAMAGE))));
        lines.add(tr("attack_speed", number(player.getAttributeValue(Attributes.ATTACK_SPEED))));
        lines.add(tr("movement", number(player.getAttributeValue(Attributes.MOVEMENT_SPEED))));
        lines.add(tr("effects").copy().withColor(GOLD));
        if (player.getActiveEffects().isEmpty()) lines.add(tr("no_effects").copy().withColor(MUTED));
        for (var effect : player.getActiveEffects()) {
            var duration = MobEffectUtil.formatDuration(effect, 1, player.level().tickRateManager().tickrate());
            lines.add(effect.getEffect().value().getDisplayName().copy()
                .append(" " + (effect.getAmplifier() + 1) + " · ").append(duration));
        }
        return lines;
    }

    private List<Component> raceLines(RacesBridge.Details details) {
        List<Component> lines = new ArrayList<>();
        if (details == null) {
            lines.add(tr("no_race").copy().withColor(MUTED));
            lines.add(tr("no_race_description"));
            return lines;
        }
        lines.add(Component.literal(details.race()).withColor(GOLD));
        lines.add(tr("benefits").copy().withColor(GOLD));
        lines.add(Component.literal(details.positive()));
        lines.add(tr("limitations").copy().withColor(GOLD));
        lines.add(Component.literal(details.negative()));
        lines.add(tr("ability").copy().withColor(GOLD));
        lines.add(Component.literal(details.ability()));
        lines.add(tr("cost", details.food()));
        lines.add(AbilitySelection.remaining(details.remainingTicks()));
        lines.add(tr("cooldown", details.cooldown()));
        if (details.curseDeaths() > 0) lines.add(tr("curse", details.curseDeaths()));
        if (!details.sign().isBlank()) lines.add(tr("sign", details.sign()));
        return lines;
    }

    /** Two decimals at most, without trailing zeros: 20.0 -> "20", 0.10 -> "0.1". */
    private static String number(double n) {
        String fixed = String.format(Locale.ROOT, "%.2f", n);
        return TRAILING_POINT.matcher(TRAILING_ZEROS.matcher(fixed).replaceAll("")).replaceAll("");
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (!query.isBlank()) {
            String haystack = (stack.getHoverName().getString() + " " + BuiltInRegistries.ITEM.getKey(stack.getItem()))
                .toLowerCase(Locale.ROOT);
            if (!haystack.contains(needle)) return false;
        }
        Item item = stack.getItem();
        boolean weapon = item instanceof SwordItem || item instanceof DiggerItem || item instanceof BowItem
            || item instanceof CrossbowItem || item instanceof TridentItem || item instanceof MaceItem;
        boolean armor = item instanceof ArmorItem || item instanceof ShieldItem || item instanceof ElytraItem;
        boolean food = stack.has(DataComponents.FOOD) || item instanceof PotionItem;
        boolean block = item instanceof BlockItem;
        return switch (category) {
            case 1 -> weapon;
            case 2 -> armor;
            case 3 -> food;
            case 4 -> block;
            case 5 -> !weapon && !armor && !food && !block;
            default -> true;
        };
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int left, int top, int button) {
        if (my < 24 || my >= height - 42) return false;
        return panels.stream().noneMatch(p -> p.contains(mx, my));
    }

    private boolean widgetCovered(AbstractWidget b) {
        return covered(widgetOwner(b), b);
    }

    private boolean covered(InventoryPanel owner, AbstractWidget b) {
        return covered(owner, b.getX(), b.getY(), b.getWidth(), b.getHeight());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        InventoryPanel top = topPane(mx, my);
        Slot slot = pickSlot(mx, my);
        if (top == powers && button == 0 && powersView.click(powers, player, mx, my)) {
            raise(powers);
            return true;
        }
        if (button == 1 && hasControlDown() && slot != null) {
            if (kind == CompatBridge.Kind.CURIOS && slot.index >= PLAYER_MENU_SLOTS) {
                CompatBridge.toggleRender(slot);
                return true;
            }
            if (CompatBridge.accessorySlot(slot)) {
                CompatBridge.accessoryRender(slot);
                return true;
            }
        }
        boolean widget = false;
        for (var b : widgets) {
            var owner = widgetOwner(b);
            if (b.visible && b.isMouseOver(mx, my) && (owner == null || owner == top) && !widgetCovered(b)) {
                widget = true;
            }
        }
        // The accessories window is docked: its title moves the whole group, its edges do not resize it.
        boolean grab = top == accessories ? top.titleContains(mx, my)
            : top != null && (top.resizeEdges(mx, my) != 0 || top.titleContains(mx, my));
        if (button == 0 && menu.getCarried().isEmpty() && !widget && grab) {
            var target = top == accessories ? equipment : top;
            raise(target);
            dragging = target;
            resizeMask = top == accessories ? 0 : top.resizeEdges(mx, my);
            anchorX = target.x;
            anchorY = target.y;
            anchorW = target.width;
            anchorH = target.height;
            dragOffsetX = (int) mx;
            dragOffsetY = (int) my;
            setFocused(null);
            return true;
        }
        if (!search.isMouseOver(mx, my) || top != items) search.setFocused(false);
        // Widgets of a window below the cursor's window must not take the click; they are switched off for this call.
        var disabled = new ArrayList<AbstractWidget>();
        for (var b : widgets) {
            var owner = widgetOwner(b);
            if (b.active && owner != null && (owner != top || widgetCovered(b))) {
                b.active = false;
                disabled.add(b);
            }
        }
        boolean searchVisible = search.visible;
        search.visible = top == items && !covered(items, search);
        try {
            if (top != null && slot == null && !widget && !search.isMouseOver(mx, my)) {
                raise(top);
                return true;
            }
            boolean result = super.mouseClicked(mx, my, button);
            if (top != null) raise(top);
            return result;
        } finally {
            disabled.forEach(b -> b.active = true);
            search.visible = searchVisible;
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging == null) return super.mouseDragged(mx, my, button, dx, dy);
        int ddx = (int) mx - dragOffsetX, ddy = (int) my - dragOffsetY;
        int nx = anchorX, ny = anchorY, right = anchorX + anchorW, bottom = anchorY + anchorH;
        if (resizeMask == 0) {
            nx += ddx;
            ny += ddy;
            right += ddx;
            bottom += ddy;
        } else {
            if ((resizeMask & InventoryPanel.LEFT) != 0) {
                nx = Math.max(InventoryPanel.MARGIN, Math.min(right - dragging.minWidth, anchorX + ddx));
            }
            if ((resizeMask & InventoryPanel.RIGHT) != 0) {
                right = Math.min(width - InventoryPanel.MARGIN, Math.max(nx + dragging.minWidth, anchorX + anchorW + ddx));
            }
            if ((resizeMask & InventoryPanel.TOP) != 0) {
                ny = Math.max(InventoryPanel.TOP_BAR, Math.min(bottom - dragging.minHeight, anchorY + ddy));
            }
            if ((resizeMask & InventoryPanel.BOTTOM) != 0) {
                bottom = Math.min(height - InventoryPanel.MARGIN, Math.max(ny + dragging.minHeight, anchorY + anchorH + ddy));
            }
        }
        dragging.x = nx;
        dragging.y = ny;
        dragging.width = right - nx;
        dragging.height = bottom - ny;
        dragging.constrain(width, height);
        if (dragging == equipment) {
            equipment.width = Math.min(equipment.width, width - items.width - 8);
            items.x = equipment.x + equipment.width;
            items.y = equipment.y;
            items.height = equipment.height;
        }
        if (dragging == items || dragging == equipment) {
            items.constrain(width, height);
            dockEquipment();
        }
        positionControls();
        positionSlots();
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging == null) return super.mouseReleased(mx, my, button);
        dragging = null;
        saveLayout();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        var top = topPane(mx, my);
        if (top == powers) {
            powersView.scroll(powers, player, my, vertical);
        } else if (top == information) {
            scroll = Math.max(0, scroll - (int) (vertical * 20));
        } else if (top == accessories) {
            scrollAccessories(vertical);
        } else if (top == items) {
            if (kind == CompatBridge.Kind.BACKPACK && my >= bagTop()) {
                extraRow = Math.max(0, extraRow - wheel.rows(vertical));
            } else {
                playerRow = Math.max(0, playerRow - wheel.rows(vertical));
            }
            positionSlots();
        } else {
            return super.mouseScrolled(mx, my, horizontal, vertical);
        }
        return true;
    }

    /** One row per wheel notch, whatever the size of the notch. */
    private void scrollAccessories(double vertical) {
        int step = vertical > 0 ? -1 : vertical < 0 ? 1 : 0;
        if (step == 0) return;
        if (classicAccessories()) {
            // The server scrolls its menu and sends the same scroll back, which moves the client's slots.
            int total = CompatBridge.integer(menu, "totalSlots", 0);
            int shown = CompatBridge.integer(menu, "scrolledIndex", 0);
            int target = Math.clamp(shown + step, 0, Math.max(0, total - CLASSIC_ROWS));
            if (target != shown) CompatBridge.accessoryScroll(target);
            return;
        }
        if (kind == CompatBridge.Kind.CURIOS && (step > 0 ? accessoryRow >= accessoryMaxRow : accessoryRow == 0)) {
            // Past the last row Curios turns its own page.
            int current = CompatBridge.integer(menu, "currentPage", 0);
            int pages = CompatBridge.integer(menu, "totalPages", 1);
            if (step > 0 ? current + 1 < pages : current > 0) {
                CompatBridge.changeCuriosPage(menu, step > 0);
                accessoryRow = step > 0 ? 0 : Integer.MAX_VALUE;
            }
            return;
        }
        accessoryRow = Math.max(0, accessoryRow + step);
        positionSlots();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (search.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                search.setFocused(false);
                setFocused(null);
                return true;
            }
            // Never let inventory shortcuts fire while entering a search query.
            search.keyPressed(key, scan, modifiers);
            return true;
        }
        if (key == GLFW.GLFW_KEY_F && hasControlDown()) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return search.isFocused() ? search.charTyped(character, modifiers) : super.charTyped(character, modifiers);
    }

    private void saveLayout() {
        var prefs = InventoryPreferences.get();
        for (var panel : panels) prefs.panels.put(layoutKey(panel), panel.save(width, height));
        prefs.save();
    }

    @Override
    public void removed() {
        saveLayout();
        original.forEach((slot, coordinates) -> place(slot, coordinates[0], coordinates[1]));
        // Accessories moved its rows while this screen scrolled; its own screen reads their y.
        int shown = classicAccessories() ? CompatBridge.integer(menu, "scrolledIndex", 0) : 0;
        accessoryRows.forEach((slot, row) ->
            place(slot, original.get(slot)[0], CLASSIC_FIRST_Y + (row - shown) * SLOT));
        if (minecraft.player != null && minecraft.player.containerMenu == menu) super.removed();
    }

    public InventoryPanel accessoriesPanel() { return accessories; }
    public InventoryPanel equipmentPanel() { return equipment; }
    public InventoryPanel itemsPanel() { return items; }
    public InventoryPanel powersPanel() { return powers; }
    public InventoryPanel informationPanel() { return information; }

    public CompatBridge.Kind integrationKind() { return kind; }
    public List<Slot> compatibilitySlots() { return extraSlots; }
}
