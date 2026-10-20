package team.civitatum.books.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import team.civitatum.books.document.PngImage;

/** Small text/search dialog, or PNG import by native file picker, path or drag-and-drop. */
final class BookDialog extends Screen implements BookChildScreen {
    private final BookScreen book;
    private final String initial;
    private final int limit;
    private final Consumer<String> accept;
    private final boolean imageImport;
    private EditBox input;
    private boolean working;
    private Component status = Component.empty();

    BookDialog(BookScreen book, String key, String initial, int limit, Consumer<String> accept) {
        super(Component.translatable(key));
        this.book = book;
        this.initial = initial;
        this.limit = limit;
        this.accept = accept;
        imageImport = false;
    }

    BookDialog(BookScreen book) {
        super(Component.translatable("books.import"));
        this.book = book;
        initial = "";
        limit = 4096;
        accept = null;
        imageImport = true;
    }

    @Override public BookScreen book() { return book; }

    @Override
    protected void init() {
        input = new EditBox(font, width / 2 - 170, height / 2 - 15, 340, 22, title);
        input.setMaxLength(limit);
        input.setValue(initial);
        addRenderableWidget(input);
        setInitialFocus(input);
        int left = width / 2 - 170;
        if (imageImport) {
            addRenderableWidget(Button.builder(Component.translatable("books.choose_file"), button -> browse())
                    .bounds(left, height / 2 + 20, 108, 22).build());
            left += 116;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> submit())
                .bounds(left, height / 2 + 20, 108, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left + 116, height / 2 + 20, 108, 22).build());
    }

    private void browse() {
        if (working) return;
        working = true;
        CompletableFuture.supplyAsync(() -> {
            try (MemoryStack memory = MemoryStack.stackPush()) {
                return TinyFileDialogs.tinyfd_openFileDialog("PNG", null, memory.pointers(memory.UTF8("*.png")), "PNG", false);
            }
        }).whenComplete((path, failure) -> minecraft.execute(() -> {
            working = false;
            if (failure != null) status = Component.translatable("books.error.failed");
            else if (path != null) {
                input.setValue(path);
                submit();
            }
        }));
    }

    private void submit() {
        if (working) return;
        if (!imageImport) {
            minecraft.setScreen(book);
            accept.accept(input.getValue());
            return;
        }
        try {
            importFile(Path.of(input.getValue()));
        } catch (RuntimeException exception) {
            status = Component.translatable("books.error.png_format");
        }
    }

    void importFile(Path path) {
        if (working) return;
        working = true;
        status = Component.translatable("books.uploading");
        CompletableFuture.supplyAsync(() -> {
            try {
                if (Files.size(path) > PngImage.MAX_BYTES) throw new IllegalArgumentException("png_size");
                return PngImage.normalize(Files.readAllBytes(path));
            } catch (Exception exception) {
                throw new java.util.concurrent.CompletionException(exception);
            }
        }).whenComplete((bytes, failure) -> minecraft.execute(() -> {
            working = false;
            if (failure == null) {
                minecraft.setScreen(book);
                book.uploadImage(bytes);
            } else {
                String reason = failure.getCause() == null ? "png_format" : failure.getCause().getMessage();
                status = Component.translatable("books.error." + ("png_size".equals(reason) ? "png_size"
                        : "png_dimensions".equals(reason) ? "png_dimensions" : "png_format"));
            }
        }));
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (imageImport && !paths.isEmpty()) importFile(paths.getFirst());
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == 257 || key == 335) {
            submit();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xf0201c18);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 50, 0xffeee1c2);
        if (imageImport) graphics.drawCenteredString(font, Component.translatable("books.png_hint"), width / 2, height / 2 - 33, 0xffbda88a);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, status, width / 2, height / 2 + 58, 0xffdfbb81);
    }

    @Override
    public void onClose() {
        if (!working) minecraft.setScreen(book);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
