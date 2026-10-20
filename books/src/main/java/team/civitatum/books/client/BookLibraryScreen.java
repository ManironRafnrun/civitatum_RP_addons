package team.civitatum.books.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Searchable local catalogue. Opening an entry never requests a book from the server. */
final class BookLibraryScreen extends Screen {
    private final Screen parent;
    private final BookShelf shelf;
    private final List<Button> rows = new ArrayList<>();
    private List<BookShelf.Entry> entries = List.of();
    private List<BookShelf.Entry> filtered = List.of();
    private EditBox search;
    private Button previous;
    private Button next;
    private int page;
    private boolean working;
    private String query = "";
    private Component status = Component.empty();

    BookLibraryScreen(Screen parent, BookShelf shelf) {
        super(Component.translatable("books.library_title"));
        this.parent = parent;
        this.shelf = shelf;
    }

    @Override
    protected void init() {
        int listWidth = Math.min(480, width - 32);
        int left = (width - listWidth) / 2;
        rows.clear();
        search = new EditBox(font, left, 45, listWidth, 20, Component.translatable("books.library_search"));
        search.setMaxLength(80);
        search.setHint(Component.translatable("books.library_search"));
        search.setValue(query);
        search.setResponder(value -> { query = value; page = 0; filter(); });
        addRenderableWidget(search);
        int count = Math.max(1, Math.min(10, (height - 130) / 30));
        for (int index = 0; index < count; index++) {
            final int row = index;
            rows.add(addRenderableWidget(Button.builder(Component.empty(), button -> open(row))
                    .bounds(left, 74 + index * 30, listWidth, 26).build()));
        }
        previous = addRenderableWidget(Button.builder(Component.translatable("books.previous"),
                button -> { page--; showPage(); }).bounds(left, height - 44, 84, 22).build());
        next = addRenderableWidget(Button.builder(Component.translatable("books.next"),
                button -> { page++; showPage(); }).bounds(left + 90, height - 44, 84, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(left + listWidth - 84, height - 44, 84, 22).build());
        load();
    }

    private void load() {
        working = true;
        status = Component.translatable("books.library_loading");
        showPage();
        CompletableFuture.supplyAsync(() -> {
            try { return shelf.entries(); }
            catch (Exception exception) { throw new java.util.concurrent.CompletionException(exception); }
        }, BookLibrary.IO).whenComplete((result, failure) -> minecraft.execute(() -> {
            if (minecraft.screen != this) return;
            working = false;
            if (failure != null) status = Component.translatable("books.library_load_failed");
            else {
                entries = result;
                status = Component.empty();
            }
            filter();
        }));
    }

    private void filter() {
        String needle = query.toLowerCase(Locale.ROOT).strip();
        filtered = entries.stream().filter(entry ->
                (entry.title() + " " + entry.author()).toLowerCase(Locale.ROOT).contains(needle)).toList();
        showPage();
    }

    private void showPage() {
        if (rows.isEmpty()) return;
        page = Math.clamp(page, 0, Math.max(0, (filtered.size() - 1) / rows.size()));
        for (int row = 0; row < rows.size(); row++) {
            int index = page * rows.size() + row;
            Button button = rows.get(row);
            button.visible = index < filtered.size();
            button.active = !working;
            if (button.visible) {
                BookShelf.Entry entry = filtered.get(index);
                String name = entry.title().isBlank() ? Component.translatable("books.untitled").getString() : entry.title();
                String author = entry.author().isBlank() ? "" : " · " + entry.author();
                String incomplete = entry.complete() ? "" : " · " + Component.translatable("books.library_partial").getString();
                button.setMessage(Component.literal(font.plainSubstrByWidth(Component.translatable("books.kind_" + entry.kind().id()).getString() + ": " + name + author + incomplete, button.getWidth() - 16)));
                button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.literal(name + author).append("\n").append(entry.kind() == team.civitatum.books.document.DocumentKind.BOOK ? Component.translatable("books.library_pages", entry.pages()) : Component.translatable("books.characters", entry.characters(), entry.kind().textLimit()))
                                .append(incomplete)));
            }
        }
        previous.active = !working && page > 0;
        next.active = !working && (page + 1) * rows.size() < filtered.size();
    }

    private void open(int row) {
        if (working) return;
        BookShelf.Entry entry = filtered.get(page * rows.size() + row);
        working = true;
        status = Component.translatable("books.library_loading");
        showPage();
        CompletableFuture.supplyAsync(() -> {
            try { return shelf.read(entry.id()); }
            catch (Exception exception) { throw new java.util.concurrent.CompletionException(exception); }
        }, BookLibrary.IO).whenComplete((book, failure) -> minecraft.execute(() -> {
            if (minecraft.screen != this) return;
            working = false;
            if (failure != null) {
                status = Component.translatable("books.library_load_failed");
                showPage();
            } else minecraft.setScreen(new BookScreen(entry.id(), book, this));
        }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xed241e18);
        graphics.drawCenteredString(font, title, width / 2, 16, 0xffe3cd9e);
        graphics.drawCenteredString(font, Component.translatable("books.library_hint"), width / 2, 31, 0xffbda88a);
        super.render(graphics, mouseX, mouseY, partialTick);
        Component footer = status;
        if (!working && status.getString().isEmpty()) footer = Component.translatable(
                entries.isEmpty() ? "books.library_empty" : "books.library_count", filtered.size());
        graphics.drawCenteredString(font, footer, width / 2, height - 59, 0xffdfbb81);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
