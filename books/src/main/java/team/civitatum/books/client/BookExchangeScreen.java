package team.civitatum.books.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import team.civitatum.books.document.BookArchive;

/** Native picker, manual path and drag-and-drop for portable books; disk IO stays off the render thread. */
final class BookExchangeScreen extends Screen implements BookChildScreen {
    private final BookScreen book;
    private final boolean exporting;
    private EditBox path;
    private boolean working;
    private Component status = Component.empty();

    BookExchangeScreen(BookScreen book, boolean exporting) {
        super(Component.translatable(exporting ? "books.export_book" : "books.import_book"));
        this.book = book;
        this.exporting = exporting;
    }

    @Override public BookScreen book() { return book; }

    @Override
    protected void init() {
        path = new EditBox(font, width / 2 - 170, height / 2 - 15, 340, 22, title);
        path.setMaxLength(4096);
        path.setValue(book.defaultArchivePath().toString());
        addRenderableWidget(path);
        setInitialFocus(path);
        addRenderableWidget(Button.builder(Component.translatable("books.choose_file"), button -> browse())
                .bounds(width / 2 - 170, height / 2 + 20, 108, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> submit())
                .bounds(width / 2 - 54, height / 2 + 20, 108, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(width / 2 + 62, height / 2 + 20, 108, 22).build());
    }

    private void browse() {
        if (working) return;
        working = true;
        String initial = path.getValue();
        CompletableFuture.supplyAsync(() -> {
            try (MemoryStack memory = MemoryStack.stackPush()) {
                var filter = memory.pointers(memory.UTF8("*.cbook"));
                return exporting ? TinyFileDialogs.tinyfd_saveFileDialog("Civitatum Books", initial, filter, "Civitatum Book")
                        : TinyFileDialogs.tinyfd_openFileDialog("Civitatum Books", initial, filter, "Civitatum Book", false);
            }
        }).whenComplete((chosen, failure) -> minecraft.execute(() -> {
            working = false;
            if (failure != null) status = Component.translatable("books.error.archive_format");
            else if (chosen != null) {
                path.setValue(chosen);
                submit();
            }
        }));
    }

    private void submit() {
        if (working) return;
        try {
            Path selected = Path.of(path.getValue()).toAbsolutePath();
            if (exporting && !selected.getFileName().toString().endsWith(".cbook")) {
                selected = selected.resolveSibling(selected.getFileName() + ".cbook");
            }
            Path destination = selected;
            if (exporting && Files.exists(destination)) {
                minecraft.setScreen(new ConfirmScreen(confirmed -> {
                    minecraft.setScreen(this);
                    if (confirmed) exchange(destination);
                }, Component.translatable("books.overwrite"), Component.literal(destination.getFileName().toString())));
            } else exchange(destination);
        } catch (RuntimeException exception) {
            status = Component.translatable("books.error.archive_format");
        }
    }

    private void exchange(Path selected) {
        if (working) return;
        working = true;
        status = Component.translatable("books.file_working");
        BookArchive.Contents snapshot = exporting ? book.archiveSnapshot() : null;
        CompletableFuture.supplyAsync(() -> {
            try {
                if (exporting) {
                    BookArchive.write(selected, snapshot.document(), snapshot.images());
                    return null;
                }
                return BookArchive.read(selected);
            } catch (Exception exception) {
                throw new java.util.concurrent.CompletionException(exception);
            }
        }).whenComplete((archive, failure) -> minecraft.execute(() -> {
            working = false;
            if (failure != null) {
                status = Component.translatable("books.error.archive_format");
                return;
            }
            minecraft.setScreen(book);
            if (exporting) book.exported(selected);
            else book.importArchive(archive);
        }));
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        if (!exporting && !files.isEmpty() && !working) {
            path.setValue(files.getFirst().toString());
            submit();
        }
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
        graphics.drawCenteredString(font, Component.translatable("books.archive_hint"), width / 2, height / 2 - 33, 0xffbda88a);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, status, width / 2, height / 2 + 58, 0xffdfbb81);
    }

    @Override public void onClose() { if (!working) minecraft.setScreen(book); }
    @Override public boolean isPauseScreen() { return false; }
}
