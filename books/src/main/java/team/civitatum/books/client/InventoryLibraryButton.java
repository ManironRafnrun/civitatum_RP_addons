package team.civitatum.books.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Optional integration through screen events; neither mod links against the other's Java classes. */
final class InventoryLibraryButton {
    private Screen inventory;
    private Button button;

    void register() {
        NeoForge.EVENT_BUS.addListener(this::init);
        NeoForge.EVENT_BUS.addListener(this::render);
        NeoForge.EVENT_BUS.addListener(this::click);
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Closing event) -> {
            if (event.getScreen() == inventory) { inventory = null; button = null; }
        });
    }

    private static boolean supports(Screen screen) {
        if (!ModList.get().isLoaded("civitatum_inventory")) return false;
        String name = screen.getClass().getName();
        if (!name.equals("team.civitatum.inventory.client.CivitatumInventoryScreen")
                && !name.equals("ru.teacraft.inventory.client.CivitatumInventoryScreen")) return false;
        var player = Minecraft.getInstance().player;
        return player != null && screen instanceof AbstractContainerScreen<?> container
                && container.getMenu() == player.inventoryMenu;
    }

    private void init(ScreenEvent.Init.Post event) {
        if (!supports(event.getScreen())) return;
        inventory = event.getScreen();
        button = new Button(inventory.width >= 388 ? inventory.width - 280 : 6,
                inventory.width >= 388 ? 4 : inventory.height - 22, 68, 18,
                Component.translatable("books.library_button"),
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
        event.addListener(button);
    }

    private void render(ScreenEvent.Render.Pre event) {
        if (event.getScreen() != inventory || button == null) return;
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
