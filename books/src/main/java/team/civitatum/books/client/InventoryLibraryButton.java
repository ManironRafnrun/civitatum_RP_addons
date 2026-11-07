package team.civitatum.books.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import team.civitatum.books.CivitatumBooks;

/** Optional integration through screen events; neither mod links against the other's Java classes. */
final class InventoryLibraryButton {
    /** Civitatum HUD, which provides the personal inventory screen since the inventory module moved into it. */
    private static final String HUD_MOD = "civitatum_hud";
    private static final String INVENTORY_SCREEN = "team.civitatum.hud.client.CivitatumInventoryScreen";
    /** The HUD's main buttons ("Character", "Powers") form a row at this height in the top left corner. */
    private static final int TOP_ROW_Y = 4;
    /** Height of the HUD's buttons, so the row stays even. */
    private static final int HEIGHT = 18;
    /** Gap between buttons of the row, as the HUD spaces "Character" and "Powers". */
    private static final int GAP = 4;
    /** Left edge used when the row is not found: the HUD's own first button starts here. */
    private static final int FALLBACK_X = 6;
    /** Text padding inside the button on both sides together. */
    private static final int PADDING = 16;
    private Screen inventory;
    private Button button;
    private boolean warned;

    void register() {
        NeoForge.EVENT_BUS.addListener(this::init);
        NeoForge.EVENT_BUS.addListener(this::render);
        NeoForge.EVENT_BUS.addListener(this::click);
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Closing event) -> {
            if (event.getScreen() == inventory) { inventory = null; button = null; }
        });
    }

    private static boolean supports(Screen screen) {
        if (!ModList.get().isLoaded(HUD_MOD) || !screen.getClass().getName().equals(INVENTORY_SCREEN)) return false;
        var player = Minecraft.getInstance().player;
        return player != null && screen instanceof AbstractContainerScreen<?> container
                && container.getMenu() == player.inventoryMenu;
    }

    private void init(ScreenEvent.Init.Post event) {
        if (!supports(event.getScreen())) return;
        inventory = event.getScreen();
        var font = Minecraft.getInstance().font;
        Component label = Component.translatable("books.library_button");
        button = new Button(0, TOP_ROW_Y, font.width(label) + PADDING, HEIGHT, label,
                pressed -> Minecraft.getInstance().setScreen(new BookLibraryScreen(inventory, BookLibrary.shelf())),
                supplier -> supplier.get()) {
            @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 500);
                graphics.fill(getX(), getY(), getX() + width, getY() + height, isHoveredOrFocused() ? 0xff66513b : 0xff493a2c);
                graphics.renderOutline(getX(), getY(), width, height, 0xff9c8056);
                graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                        getY() + 5, active ? 0xffe9e3d4 : 0xffaba799);
                graphics.pose().popPose();
            }
        };
        button.setTooltip(Tooltip.create(Component.translatable("books.library_hint")));
        place();
        event.addListener(button);
    }

    /**
     * Next to the right-most HUD button of the top left row. The HUD places "Powers" after "Character" in the narrow
     * layout and after the screen title in the wide one, so the position is read from its widgets, not assumed.
     */
    private void place() {
        int right = -1;
        for (var child : inventory.children()) {
            if (child != button && child instanceof AbstractWidget widget && widget.visible
                    && widget.getY() == TOP_ROW_Y && widget.getX() < inventory.width / 2) {
                right = Math.max(right, widget.getX() + widget.getWidth());
            }
        }
        if (right < 0 && !warned) {
            warned = true;
            CivitatumBooks.LOG.warn("Civitatum HUD inventory has no buttons in its top left row; "
                    + "the Books button is placed at the left edge");
        }
        button.setPosition(right < 0 ? FALLBACK_X : right + GAP, TOP_ROW_Y);
    }

    private void render(ScreenEvent.Render.Pre event) {
        if (event.getScreen() != inventory || button == null) return;
        place();
        button.active = supports(inventory) && Minecraft.getInstance().player.containerMenu.getCarried().isEmpty();
    }

    private void click(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() == inventory && button != null && supports(inventory)
                && Minecraft.getInstance().player.containerMenu.getCarried().isEmpty()
                && button.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }
}
