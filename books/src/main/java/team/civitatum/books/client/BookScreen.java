package team.civitatum.books.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import team.civitatum.books.CivitatumBooks;
import team.civitatum.books.document.BookDocument;
import team.civitatum.books.document.BookArchive;
import team.civitatum.books.document.DocumentKind;
import net.minecraft.resources.ResourceLocation;
import team.civitatum.books.document.BookPage;
import team.civitatum.books.document.TextLayout;
import team.civitatum.books.network.BookNetwork;
import team.civitatum.books.network.UploadKind;

/** Читатель и редактор документов с общими координатами отрисовки и ввода. */
public final class BookScreen extends Screen {
    private static final int CANVAS_WIDTH = 1000;
    private static final int CANVAS_HEIGHT = 600;
    private static final int PAGE_LEFT = 164;
    private static final int PAGE_TOP = 102;
    private static final int PAGE_WIDTH = 400;
    private static final int PAGE_HEIGHT = 422;
    private static final int MARGIN = 25;
    private static final int SHEET_SIZE = 500;
    private static final int SHEET_TEXTURE_LEFT = 318;
    private static final int SHEET_TEXTURE_TOP = 96;
    private static final int SHEET_CANVAS_HEIGHT = 660;
    private static final int SHEET_READER_CONTROLS_TOP = 610;
    private static final int[] COLORS = {0x30251a, 0x923c32, 0x3c674e, 0x3f587c};
    private final UUID session;
    private final UUID book;
    private final boolean lectern;
    private final boolean administrative;
    private final boolean localCopy;
    private final Screen returnTo;
    private final BookLibrary.Capture capture;
    private static final int SHEET_LEFT = 368;
    private static final ResourceLocation OPEN_SCROLL = CivitatumBooks.resource("textures/gui/open_scroll.png");
    private static final ResourceLocation OPEN_NOTE = CivitatumBooks.resource("textures/gui/open_note.png");
    private final Map<BookPage, List<TextLayout.Line>> lineCache = new java.util.IdentityHashMap<>();
    private int scrollOffset;
    private static final ResourceLocation OPEN_BOOK = CivitatumBooks.resource("textures/gui/open_book.png");
    private final BookImages images = new BookImages();
    private final DraftStore drafts;
    private final Set<String> requestedImages = new HashSet<>();
    private final Map<Integer, Integer> glyphWidths = new HashMap<>();
    private final List<Control> controls = new ArrayList<>();
    private final BookEditor editor;
    private boolean editable;
    private boolean reading;
    private boolean expanded;
    private BookImport pendingImport;
    private boolean dirty;
    private boolean busy;
    private boolean saveFailed;
    private boolean closeAfterSave;
    private boolean draggingText;
    private boolean resizingImage;
    private ImageBox resizeOrigin;
    private boolean imageSelected;
    private boolean firstLayout = true;
    private int sidebarStart;
    private int uploadPage;
    private int ticks;
    private int colorIndex;
    private long changedAt;
    private long backupAt;
    private long requestAt;
    private String sentDocument = "";
    private String search = "";
    private Component status = Component.translatable("books.saved");
    private BookDocument recovery;
    private float scale;
    private float originX;
    private float originY;

    public BookScreen(BookNetwork.OpenBook packet) {
        super(Component.literal("Civitatum Books"));
        localCopy = false;
        returnTo = null;
        capture = BookLibrary.opened(packet);
        session = packet.session();
        book = packet.book();
        lectern = packet.lectern();
        editable = packet.editable();
        administrative = packet.administrative();
        reading = !administrative;
        expanded = administrative;
        editor = new BookEditor(BookDocument.parse(packet.document()));
        drafts = new DraftStore(Minecraft.getInstance().gameDirectory.toPath(), book);
        try {
            recovery = editable ? drafts.recover(editor.document) : null;
        } catch (Exception exception) {
            CivitatumBooks.LOG.warn("Cannot read local book draft {}", book, exception);
        }
        requestImages();
    }

    /** A retained copy is strictly read-only and never opens a network session. */
    BookScreen(UUID id, BookArchive.Contents saved, Screen parent) {
        super(Component.literal("Civitatum Books"));
        session = UUID.randomUUID();
        book = id;
        lectern = false;
        administrative = false;
        localCopy = true;
        returnTo = parent;
        capture = null;
        drafts = null;
        editable = false;
        reading = true;
        expanded = false;
        editor = new BookEditor(saved.document().copy());
        status = text("library_copy");
        for (var image : saved.images().entrySet()) {
            try {
                images.accept(new BookNetwork.Image(session, image.getKey(), image.getValue()));
            } catch (Exception exception) {
                CivitatumBooks.LOG.warn("Cannot decode retained book illustration {}", image.getKey(), exception);
            }
        }
    }

    @Override
    protected void init() {
        updateGeometry();
        if (firstLayout) {
            firstLayout = false;
            String original = editor.document.json();
            if (editable) {
                try {
                    editor.paginate(this::glyphWidth);
                    if (!original.equals(editor.document.json())) changed();
                } catch (RuntimeException exception) {
                    editor.document = BookDocument.parse(original);
                    status = error("pages");
                }
            }
            editor.clearHistory();
        }
    }

    private void updateGeometry() {
        float canvasWidth = expanded ? CANVAS_WIDTH : isBook() ? 912 : SHEET_SIZE;
        float canvasHeight = expanded ? editorCanvasHeight() : isBook() ? 540 : SHEET_SIZE + 60;
        scale = Math.min((width - 16f) / canvasWidth, (height - 16f) / canvasHeight);
        originX = (width - canvasWidth * scale) / 2 - (expanded ? 0 : (isBook() ? 112 : SHEET_TEXTURE_LEFT) * scale);
        originY = (height - canvasHeight * scale) / 2 - (expanded ? 0 : (isBook() ? 72 : SHEET_TEXTURE_TOP) * scale);
    }

    private void changeView(boolean readOnly, boolean expandedView) {
        reading = readOnly;
        expanded = expandedView;
        imageSelected = false;
        updateGeometry();
    }

    private boolean editing() { return editable && !reading && !busy; }
    private boolean isBook() { return editor.document.kind == DocumentKind.BOOK; }
    private boolean isScroll() { return editor.document.kind == DocumentKind.SCROLL; }
    private int spread() { return isBook() ? editor.page / 2 * 2 : 0; }
    private int pageX() { return isBook() ? PAGE_LEFT + editor.page % 2 * (PAGE_WIDTH + 8) : SHEET_LEFT; }
    private int editorCanvasHeight() { return isBook() ? CANVAS_HEIGHT : SHEET_CANVAS_HEIGHT; }
    private int footerTop() { return editorCanvasHeight() - 46; }
    private int contentTop() { return isBook() ? PAGE_TOP + MARGIN : isScroll() ? 196 : 151; }
    private int viewportHeight() { return isScroll() ? 310 : BookEditor.PAGE_HEIGHT; }
    private int contentBottom() { return contentTop() + viewportHeight(); }

    private void changeKind(DocumentKind kind) {
        if (!editing()) return;
        try {
            editor.changeKind(kind, this::glyphWidth);
            scrollOffset = 0;
            imageSelected = false;
            changed();
        } catch (RuntimeException exception) {
            status = error(exception.getMessage().equals("mode_images") ? "mode_images" : "note_full");
        }
    }

    private int scrollMaximum() {
        if (!isScroll()) return 0;
        BookPage page = editor.currentPage();
        int height = lines(page).size() * BookEditor.lineHeight(page)
                + (page.image.isEmpty() ? 0 : page.imageHeight + 16);
        return Math.max(0, height - viewportHeight());
    }

    private void scrollBy(int pixels) { scrollOffset = Math.clamp(scrollOffset + pixels, 0, scrollMaximum()); }

    private void revealCursor() {
        if (!isScroll()) return;
        BookPage page = editor.currentPage();
        int y = rowAt(lines(page), editor.cursor) * BookEditor.lineHeight(page)
                + (!page.image.isEmpty() && !page.imageBottom ? page.imageHeight + 16 : 0);
        if (y < scrollOffset) scrollOffset = y;
        if (y + BookEditor.lineHeight(page) > scrollOffset + viewportHeight())
            scrollOffset = y + BookEditor.lineHeight(page) - viewportHeight();
        scrollOffset = Math.clamp(scrollOffset, 0, scrollMaximum());
    }
    private float localX(double x) { return (float) ((x - originX) / scale); }
    private float localY(double y) { return (float) ((y - originY) / scale); }
    private static Component text(String key) { return Component.translatable("books." + key); }
    private static Component error(String key) { return Component.translatable("books.error." + key); }

    private record Control(int x, int y, int width, Component label, Runnable action, boolean enabled, boolean selected) {
        boolean contains(float mouseX, float mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 27;
        }
    }

    private void control(int x, int y, int width, Component label, Runnable action, boolean enabled, boolean selected) {
        controls.add(new Control(x, y, width, label, action, enabled && !busy, selected));
    }

    private void button(int x, int y, int width, String key, Runnable action, boolean enabled) {
        control(x, y, width, text(key), action, enabled, false);
    }

    private void buildControls() {
        controls.clear();
        if (!expanded) {
            control(isBook() ? 954 : 780, isBook() ? 79 : 104, 28, Component.literal("+"), () -> changeView(true, true), true, false);
            if (lectern) button(isBook() ? 174 : 330, isBook() ? 517 : SHEET_READER_CONTROLS_TOP, 136, "take", this::takeBook, !dirty);
            if (!isBook()) {
                if (isScroll()) {
                    control(478, SHEET_READER_CONTROLS_TOP, 60, Component.literal("↑"), () -> scrollBy(-viewportHeight()), scrollOffset > 0, false);
                    control(552, SHEET_READER_CONTROLS_TOP, 60, Component.literal("↓"), () -> scrollBy(viewportHeight()), scrollOffset < scrollMaximum(), false);
                }
                button(650, SHEET_READER_CONTROLS_TOP, 130, "close", this::onClose, true);
                return;
            }
            control(438, 517, 64, text("previous"), () -> selectPage(spread() - 2), spread() > 0, false);
            control(622, 517, 64, text("next"), () -> selectPage(spread() + 2), spread() + 2 < editor.document.pages.size(), false);
            button(824, 517, 132, "close", this::onClose, true);
            return;
        }
        String title = editor.document.title.isBlank() ? text("untitled").getString() : editor.document.title;
        control(288, 14, 382, Component.literal(title), this::editTitle, editing(), false);
        control(705, 14, 105, text("read"), () -> changeView(true, false), true, reading);
        control(818, 14, 124, text("edit"), () -> changeView(false, true), editable, !reading);
        control(952, 14, 30, Component.literal("×"), this::onClose, true, false);
        if (!reading && editable) {
            control(174, 61, 34, Component.literal("B").withStyle(Style.EMPTY.withBold(true)), () -> format(BookPage.BOLD, null), true, false);
            control(212, 61, 34, Component.literal("I").withStyle(Style.EMPTY.withItalic(true)), () -> format(BookPage.ITALIC, null), true, false);
            control(254, 61, 68, text("color"), () -> format(0, COLORS[(++colorIndex) % COLORS.length]), true, false);
            control(330, 61, 28, Component.literal("−"), () -> fontSize(-1), editor.currentPage().fontSize > 9, false);
            control(360, 61, 36, Component.literal(Integer.toString(editor.currentPage().fontSize)), () -> {}, false, false);
            control(398, 61, 28, Component.literal("+"), () -> fontSize(1), editor.currentPage().fontSize < 14, false);
            button(434, 61, 96, "align_" + editor.currentPage().alignment, () -> mutate(() ->
                    editor.currentPage().alignment = (editor.currentPage().alignment + 1) % 3, true), true);
            button(536, 61, 58, "undo", this::undo, true);
            button(600, 61, 58, "redo", this::redo, true);
            button(664, 61, 98, "add_png", this::importImage, true);
            if (recovery != null) button(872, 61, 108, "recover", this::recover, true);
        }
        button(770, 61, 93, "search", this::searchDialog, true);
        button(16, 383, 130, "export_book", () -> minecraft.setScreen(new BookExchangeScreen(this, true)), exportReady());
        button(16, 414, 130, "import_book", () -> confirmImport(() -> minecraft.setScreen(new BookExchangeScreen(this, false))), editing());
        if (administrative) button(16, 514, 130, "author", this::editAuthor, editing());
        if (!reading && editable) {
            for (DocumentKind kind : DocumentKind.values()) {
                control(16, 277 + kind.ordinal() * 31, 130, text("kind_" + kind.id()),
                        () -> changeKind(kind), editing(), editor.document.kind == kind);
            }
        }
        if (isBook()) {
            button(16, 445, 130, "add_page", () -> mutate(() -> {
                if (editor.document.pages.size() >= BookDocument.MAX_PAGES) throw new IllegalArgumentException("pages");
                editor.document.pages.add(editor.page + 1, new BookPage());
                editor.selectPage(editor.page + 1);
            }, false), editing());
            control(16, 478, 38, Component.literal("↑"), () -> movePage(-1), editing() && editor.page > 0, false);
            control(60, 478, 38, Component.literal("↓"), () -> movePage(1), editing() && editor.page + 1 < editor.document.pages.size(), false);
            control(104, 478, 42, Component.literal("×"), this::deletePage, editing(), false);
            control(418, 554, 36, Component.literal("‹"), () -> selectPage(spread() - 2), spread() > 0, false);
            control(598, 554, 36, Component.literal("›"), () -> selectPage(spread() + 2), spread() + 2 < editor.document.pages.size(), false);
            }
        if (!reading && editable) {
            button(756, footerTop(), 104, "save", () -> save(false, false), dirty);
            if (!editor.document.signed) button(870, footerTop(), 112, "sign", this::confirmSign, true);
        } else if (lectern) {
            button(870, footerTop(), 112, "take", this::takeBook, !dirty);
        }
        if (imageSelected && editing()) {
            button(isBook() ? 174 : 16, isBook() ? 529 : 445, isBook() ? 140 : 130, editor.currentPage().imageBottom ? "image_top" : "image_bottom",
                    () -> mutate(() -> editor.currentPage().imageBottom = !editor.currentPage().imageBottom, true), true);
            button(isBook() ? 322 : 16, isBook() ? 529 : 478, isBook() ? 128 : 130, "remove_image", () -> mutate(() -> {
                editor.currentPage().image = "";
                imageSelected = false;
            }, false), true);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xc00d1012);
        graphics.pose().pushPose();
        graphics.pose().translate(originX, originY, 0);
        graphics.pose().scale(scale, scale, 1);
        if (expanded) {
            int canvasHeight = editorCanvasHeight();
            graphics.fill(0, 0, CANVAS_WIDTH, canvasHeight, 0xff2a211b);
            graphics.fill(5, 5, CANVAS_WIDTH - 5, canvasHeight - 5, 0xff49382b);
            graphics.fill(8, 48, CANVAS_WIDTH - 8, footerTop() - 5, 0xff30281f);
            graphics.drawString(font, "CIVITATUM  /  BOOKS", 18, 24, 0xffe3cd9e, false);
            graphics.drawString(font, text(isBook() ? "pages" : "kind_" + editor.document.kind.id()), 18, 66, 0xffc7ae7f, false);
            if (isBook()) drawSidebar(graphics);
            else {
                graphics.drawString(font, Component.translatable("books.characters", editor.document.characterCount(), editor.document.kind.textLimit()), 18, 108, 0xffc7ae7f, false);
                if (isScroll()) graphics.drawString(font, text("scroll_hint"), 18, 135, 0xffc7ae7f, false);
            }
        } else if (isBook()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.blit(OPEN_BOOK, 112, 72, 912, 540, 0, 0, 1650, 953, 1650, 953);
            RenderSystem.disableBlend();
        }
        if (isBook()) {
            drawPage(graphics, spread(), PAGE_LEFT);
            drawPage(graphics, spread() + 1, PAGE_LEFT + PAGE_WIDTH + 8);
        } else {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.blit(isScroll() ? OPEN_SCROLL : OPEN_NOTE, SHEET_TEXTURE_LEFT, SHEET_TEXTURE_TOP,
                    SHEET_SIZE, SHEET_SIZE, 0, 0, 1254, 1254, 1254, 1254);
            RenderSystem.disableBlend();
            scrollOffset = Math.clamp(scrollOffset, 0, scrollMaximum());
            // Отсечение, прокрутка и мышь используют одну и ту же область бумаги.
            graphics.pose().pushPose();
            graphics.pose().last().pose().identity();
            graphics.enableScissor(Math.round(originX + (SHEET_LEFT + MARGIN) * scale),
                    Math.round(originY + contentTop() * scale),
                    Math.round(originX + (SHEET_LEFT + PAGE_WIDTH - MARGIN) * scale),
                    Math.round(originY + contentBottom() * scale));
            graphics.pose().popPose();
            drawPage(graphics, 0, SHEET_LEFT);
            graphics.disableScissor();
            if (editor.document.signed) graphics.drawString(font, font.plainSubstrByWidth(editor.document.author, 300),
                    SHEET_LEFT + MARGIN, contentBottom() + (isScroll() ? 6 : 16), 0xff8b7555, false);
            if (isScroll() && scrollMaximum() > 0) {
                int thumb = Math.max(16, viewportHeight() * viewportHeight() / (scrollMaximum() + viewportHeight()));
                int top = contentTop() + scrollOffset * (viewportHeight() - thumb) / scrollMaximum();
                graphics.fill(752, contentTop(), 756, contentBottom(), 0x40563e24);
                graphics.fill(752, top, 756, top + thumb, 0xff927044);
            }
        }
        if (expanded) {
            if (isBook()) {
                graphics.fill(564, PAGE_TOP + 1, 572, PAGE_TOP + PAGE_HEIGHT - 1, 0xff9c835f);
                graphics.drawCenteredString(font, Component.translatable("books.page_count", editor.page + 1, editor.document.pages.size()), 526, 563, 0xffe3cd9e);
            }
            Component visibleStatus = status;
            graphics.drawString(font, font.plainSubstrByWidth(visibleStatus.getString(), 382), 20, footerTop() + 10, saveFailed ? 0xffffb0a0 : 0xffd6c09a, false);
        }
        buildControls();
        float localMouseX = localX(mouseX);
        float localMouseY = localY(mouseY);
        for (Control control : controls) {
            if (!expanded) {
                if (control.label.getString().equals("+")) {
                    graphics.fill(control.x, control.y, control.x + control.width, control.y + 27, 0xffd8c18b);
                    graphics.renderOutline(control.x, control.y, control.width, 27, 0xff7c5935);
                }
                int color = control.enabled && control.contains(localMouseX, localMouseY) ? 0xff884626 : control.enabled ? 0xff493321 : 0xffb39b75;
                graphics.drawString(font, control.label, control.x + (control.width - font.width(control.label)) / 2, control.y + 9, color, false);
                if (control.label.getString().equals("+") && control.contains(localMouseX, localMouseY)) {
                    graphics.drawString(font, text("expand"), isBook() ? 785 : 600, isBook() ? 91 : 116, 0xff493321, false);
                }
                continue;
            }
            int background = control.selected ? 0xff6c7154 : control.enabled && control.contains(localMouseX, localMouseY) ? 0xff756048 : 0xff514132;
            graphics.fill(control.x, control.y, control.x + control.width, control.y + 27, background);
            graphics.fill(control.x, control.y + 26, control.x + control.width, control.y + 27, 0xffa08760);
            graphics.drawCenteredString(font, font.plainSubstrByWidth(control.label.getString(), control.width - 6), control.x + control.width / 2,
                    control.y + 9, control.enabled ? 0xffeee1c2 : 0xffa2937e);
        }
        graphics.pose().popPose();
    }

    private int sidebarRows() { return !reading && editable ? 3 : 5; }

    private void drawSidebar(GuiGraphics graphics) {
        sidebarStart = Math.clamp(sidebarStart, 0, Math.max(0, editor.document.pages.size() - sidebarRows()));
        for (int row = 0; row < sidebarRows() && sidebarStart + row < editor.document.pages.size(); row++) {
            int index = sidebarStart + row;
            int y = 105 + row * 54;
            graphics.fill(16, y, 146, y + 47, index == editor.page ? 0xff756448 : 0xff473a2e);
            graphics.fill(24, y + 8, 44, y + 37, 0xffe1d1ad);
            graphics.drawString(font, Integer.toString(index + 1), 28, y + 18, 0xff493a27, false);
            BookPage page = editor.document.pages.get(index);
            graphics.drawString(font, font.plainSubstrByWidth(page.text().replace('\n', ' '), 86), 51, y + 13, 0xffd9c7a6, false);
            if (!page.image.isEmpty()) graphics.drawString(font, "PNG", 51, y + 28, 0xffb6c29c, false);
        }
    }

    private void drawPage(GuiGraphics graphics, int index, int x) {
        if (expanded && isBook()) {
            graphics.fill(x + 3, PAGE_TOP + 4, x + PAGE_WIDTH + 3, PAGE_TOP + PAGE_HEIGHT + 4, 0xff17120d);
            graphics.fill(x, PAGE_TOP, x + PAGE_WIDTH, PAGE_TOP + PAGE_HEIGHT, 0xffb69e73);
            graphics.fill(x + 2, PAGE_TOP + 2, x + PAGE_WIDTH - 2, PAGE_TOP + PAGE_HEIGHT - 2, 0xffeee1c2);
        }
        if (index >= editor.document.pages.size()) return;
        BookPage page = editor.document.pages.get(index);
        if (!page.image.isEmpty()) drawImage(graphics, page, x, index == editor.page && imageSelected);
        var lines = lines(page);
        int cursorLine = rowAt(lines, editor.cursor);
        int y = textY(page);
        int capacity = isScroll() ? lines.size() : Math.max(1, BookEditor.textHeight(page) / BookEditor.lineHeight(page));
        int firstRow = isScroll() ? Math.max(0, (contentTop() - y) / BookEditor.lineHeight(page)) : 0;
        y += firstRow * BookEditor.lineHeight(page);
        for (int row = firstRow; row < Math.min(lines.size(), capacity); row++) {
            if (isScroll() && y >= contentBottom()) break;
            TextLayout.Line line = lines.get(row);
            float runX = lineX(page, line, x);
            float glyphX = runX;
            for (int offset = line.start(); offset < line.end();) {
                int codePoint = page.text().codePointAt(offset);
                float advance = advance(page, codePoint, page.styleAt(offset));
                if (index == editor.page && offset >= editor.selectionStart() && offset < editor.selectionEnd()) {
                    graphics.fill((int) glyphX, y - 1, (int) Math.ceil(glyphX + advance), y + page.fontSize + 2, 0x554c82a0);
                }
                glyphX += advance;
                offset += Character.charCount(codePoint);
            }
            for (int start = line.start(); start < line.end();) {
                int style = page.styleAt(start);
                int end = TextLayout.next(page.text(), start);
                while (end < line.end() && page.styleAt(end) == style) end = TextLayout.next(page.text(), end);
                Component run = Component.literal(page.text().substring(start, end)).withStyle(style(style));
                graphics.pose().pushPose();
                graphics.pose().translate(runX, y, 0);
                float fontScale = page.fontSize / 9f;
                graphics.pose().scale(fontScale, fontScale, 1);
                graphics.drawString(font, run, 0, 0, 0xff30251a, false);
                graphics.pose().popPose();
                runX += font.width(run) * fontScale;
                start = end;
            }
            if (index == editor.page && !reading && editable && row == cursorLine && ticks % 12 < 6) {
                float caretX = lineX(page, line, x) + widthBetween(page, line.start(), Math.min(editor.cursor, line.end()));
                graphics.fill((int) caretX, y - 1, (int) caretX + 1, y + page.fontSize + 2, 0xff30251a);
            }
            y += BookEditor.lineHeight(page);
        }
        if (isBook()) graphics.drawCenteredString(font, Integer.toString(index + 1), x + PAGE_WIDTH / 2, PAGE_TOP + PAGE_HEIGHT - 18, 0xff8b7555);
        if (isBook() && editor.document.signed && index == 0) {
            graphics.drawString(font, font.plainSubstrByWidth(editor.document.author, 175), x + 16, PAGE_TOP + PAGE_HEIGHT - 18, 0xff8b7555, false);
        }
    }

    private record ImageBox(int x, int y, int width, int height) {
        boolean contains(float mouseX, float mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private ImageBox imageBox(BookPage page, int pageX) {
        int imageWidth = (int) (BookEditor.PAGE_WIDTH * page.imageWidth);
        int y = page.imageBottom ? isScroll() ? contentTop() + lines(page).size() * BookEditor.lineHeight(page) + 16
                : contentBottom() - page.imageHeight : contentTop();
        if (isScroll()) y -= scrollOffset;
        return new ImageBox(pageX + (PAGE_WIDTH - imageWidth) / 2, y, imageWidth, page.imageHeight);
    }

    private void drawImage(GuiGraphics graphics, BookPage page, int pageX, boolean selected) {
        ImageBox box = imageBox(page, pageX);
        BookImages.Image image = images.get(page.image);
        graphics.fill(box.x, box.y, box.x + box.width, box.y + box.height, 0x12816c4d);
        if (image == null) graphics.drawCenteredString(font, localCopy ? text("library_missing_image").getString() : "PNG…", box.x + box.width / 2, box.y + box.height / 2, 0xff867152);
        else {
            float fit = Math.min(box.width / (float) image.width(), box.height / (float) image.height());
            int drawWidth = Math.round(image.width() * fit);
            int drawHeight = Math.round(image.height() * fit);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.blit(image.texture(), box.x + (box.width - drawWidth) / 2, box.y + (box.height - drawHeight) / 2,
                    drawWidth, drawHeight, 0, 0, image.width(), image.height(), image.width(), image.height());
            RenderSystem.disableBlend();
        }
        if (selected) {
            graphics.renderOutline(box.x - 1, box.y - 1, box.width + 2, box.height + 2, 0xff5e7957);
            graphics.fill(box.x + box.width - 5, box.y + box.height - 5, box.x + box.width + 4, box.y + box.height + 4, 0xff5e7957);
        }
    }

    private static Style style(int encoded) {
        return Style.EMPTY.withColor(encoded >>> 2).withBold((encoded & BookPage.BOLD) != 0).withItalic((encoded & BookPage.ITALIC) != 0);
    }

    private float glyphWidth(int codePoint, int encodedStyle) {
        int key = codePoint << 2 | encodedStyle & 3;
        return glyphWidths.computeIfAbsent(key, ignored -> font.width(Component.literal(new String(Character.toChars(codePoint))).withStyle(style(encodedStyle))));
    }

    private float advance(BookPage page, int codePoint, int encodedStyle) {
        return glyphWidth(codePoint, encodedStyle) * page.fontSize / 9f;
    }

    private List<TextLayout.Line> lines(BookPage page) {
        return lineCache.computeIfAbsent(page, value -> TextLayout.wrap(value, BookEditor.PAGE_WIDTH, (codePoint, encoded) -> advance(value, codePoint, encoded)));
    }

    private int textY(BookPage page) {
        return contentTop() + (!page.image.isEmpty() && !page.imageBottom ? page.imageHeight + 16 : 0) - (isScroll() ? scrollOffset : 0);
    }

    private float lineX(BookPage page, TextLayout.Line line, int pageX) {
        return pageX + MARGIN + (page.alignment == 1 ? (BookEditor.PAGE_WIDTH - line.width()) / 2
                : page.alignment == 2 ? BookEditor.PAGE_WIDTH - line.width() : 0);
    }

    private float widthBetween(BookPage page, int start, int end) {
        float result = 0;
        for (int offset = start; offset < end; offset = TextLayout.next(page.text(), offset)) {
            result += advance(page, page.text().codePointAt(offset), page.styleAt(offset));
        }
        return result;
    }

    private static int rowAt(List<TextLayout.Line> lines, int cursor) {
        int low = 0, high = lines.size() - 1;
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (lines.get(middle).start() <= cursor) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    private int hit(BookPage page, int pageX, float mouseX, float mouseY) {
        var lines = lines(page);
        int row = Math.clamp((int) ((mouseY - textY(page)) / BookEditor.lineHeight(page)), 0, lines.size() - 1);
        TextLayout.Line line = lines.get(row);
        float x = lineX(page, line, pageX);
        for (int offset = line.start(); offset < line.end(); offset = TextLayout.next(page.text(), offset)) {
            float advance = advance(page, page.text().codePointAt(offset), page.styleAt(offset));
            if (mouseX < x + advance / 2) return offset;
            x += advance;
        }
        return line.end();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || busy) return false;
        float x = localX(mouseX);
        float y = localY(mouseY);
        buildControls();
        for (Control control : controls) {
            if (control.enabled && control.contains(x, y)) {
                control.action.run();
                return true;
            }
        }
        if (expanded && isBook() && x >= 16 && x <= 146 && y >= 105 && y < 105 + sidebarRows() * 54) {
            int index = sidebarStart + (int) ((y - 105) / 54);
            if (index < editor.document.pages.size()) selectPage(index);
            return true;
        }
        int index = isBook() ? x < PAGE_LEFT + PAGE_WIDTH ? spread() : spread() + 1 : 0;
        int pageX = isBook() ? PAGE_LEFT + (index % 2) * (PAGE_WIDTH + 8) : SHEET_LEFT;
        int hitLeft = isBook() ? pageX : pageX + MARGIN;
        int hitRight = isBook() ? pageX + PAGE_WIDTH : pageX + PAGE_WIDTH - MARGIN;
        int hitTop = isBook() ? PAGE_TOP : contentTop();
        int hitBottom = isBook() ? PAGE_TOP + PAGE_HEIGHT : contentBottom();
        if (x < hitLeft || x > hitRight || y < hitTop || y > hitBottom
                || index >= editor.document.pages.size()) return false;
        if (index != editor.page) editor.selectPage(index);
        BookPage page = editor.currentPage();
        ImageBox box = imageBox(page, pageX);
        imageSelected = !page.image.isEmpty() && box.contains(x, y);
        if (imageSelected) {
            if (editing() && x > box.x + box.width - 14 && y > box.y + box.height - 14) {
                editor.remember();
                resizingImage = true;
                resizeOrigin = box;
            }
        } else {
            editor.cursor = hit(page, pageX, x, y);
            if (!hasShiftDown()) editor.anchor = editor.cursor;
            draggingText = true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (busy || button != 0) return false;
        BookPage page = editor.currentPage();
        int pageX = pageX();
        if (resizingImage && editing()) {
            ImageBox box = resizeOrigin;
            page.imageWidth = Math.clamp((localX(mouseX) - box.x) / BookEditor.PAGE_WIDTH, 0.25f, 1f);
            page.imageHeight = Math.clamp((int) (localY(mouseY) - box.y), 40, 240);
            return true;
        }
        if (draggingText) {
            if (isScroll() && localY(mouseY) < contentTop()) scrollBy(-BookEditor.lineHeight(page));
            if (isScroll() && localY(mouseY) > contentBottom()) scrollBy(BookEditor.lineHeight(page));
            editor.cursor = hit(page, pageX, localX(mouseX), localY(mouseY));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingText = false;
        if (resizingImage) {
            resizingImage = false;
            try {
                editor.paginate(this::glyphWidth);
                changed();
            } catch (RuntimeException exception) {
                editor.undo();
                status = error("pages");
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (busy || vertical == 0) return false;
        if (isScroll()) scrollBy((int) (-vertical * BookEditor.lineHeight(editor.currentPage()) * 3));
        else if (!isBook()) return false;
        else if (expanded && localX(mouseX) < 154) sidebarStart += vertical < 0 ? 1 : -1;
        else selectPage(spread() + (vertical < 0 ? 2 : -2));
        return true;
    }

    private void selectPage(int index) {
        editor.selectPage(index);
        imageSelected = false;
        if (editor.page < sidebarStart || editor.page >= sidebarStart + sidebarRows()) sidebarStart = editor.page;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (!editing() || character < 32 || character == 127) return false;
        insert(Character.toString(character));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_F6 && !busy) {
            changeView(true, !expanded);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (busy) return true;
        boolean control = hasControlDown();
        if (control && key == GLFW.GLFW_KEY_F) {
            searchDialog();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F3) {
            findNext();
            return true;
        }
        if (control && key == GLFW.GLFW_KEY_C) {
            minecraft.keyboardHandler.setClipboard(editor.selection());
            return true;
        }
        if (control && key == GLFW.GLFW_KEY_A) {
            editor.anchor = 0;
            editor.cursor = editor.currentPage().text().length();
            return true;
        }
        if (isScroll() && (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN
                || !editing() && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN))) {
            int direction = key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_UP ? -1 : 1;
            scrollBy(direction * (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN ? BookEditor.lineHeight(editor.currentPage()) : viewportHeight()));
            return true;
        }
        if (!editing()) {
            if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP) selectPage(spread() - 2);
            else if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN) selectPage(spread() + 2);
            else return super.keyPressed(key, scanCode, modifiers);
            return true;
        }
        if (control) {
            switch (key) {
                case GLFW.GLFW_KEY_Z -> { if (hasShiftDown()) redo(); else undo(); }
                case GLFW.GLFW_KEY_Y -> redo();
                case GLFW.GLFW_KEY_S -> save(false, false);
                case GLFW.GLFW_KEY_V -> insert(minecraft.keyboardHandler.getClipboard());
                case GLFW.GLFW_KEY_X -> {
                    minecraft.keyboardHandler.setClipboard(editor.selection());
                    insert("");
                }
                case GLFW.GLFW_KEY_B -> format(BookPage.BOLD, null);
                case GLFW.GLFW_KEY_I -> format(BookPage.ITALIC, null);
                default -> { return super.keyPressed(key, scanCode, modifiers); }
            }
            return true;
        }
        String value = editor.currentPage().text();
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (editor.cursor == editor.anchor) editor.anchor = TextLayout.previous(value, editor.cursor);
                insert("");
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (editor.cursor == editor.anchor) editor.anchor = TextLayout.next(value, editor.cursor);
                insert("");
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> insert("\n");
            case GLFW.GLFW_KEY_LEFT -> moveCursor(TextLayout.previous(value, editor.cursor));
            case GLFW.GLFW_KEY_RIGHT -> moveCursor(TextLayout.next(value, editor.cursor));
            case GLFW.GLFW_KEY_HOME -> moveCursor(lines(editor.currentPage()).get(rowAt(lines(editor.currentPage()), editor.cursor)).start());
            case GLFW.GLFW_KEY_END -> moveCursor(lines(editor.currentPage()).get(rowAt(lines(editor.currentPage()), editor.cursor)).end());
            case GLFW.GLFW_KEY_UP -> verticalCursor(-1);
            case GLFW.GLFW_KEY_DOWN -> verticalCursor(1);
            case GLFW.GLFW_KEY_PAGE_UP -> selectPage(spread() - 2);
            case GLFW.GLFW_KEY_PAGE_DOWN -> selectPage(spread() + 2);
            default -> { return super.keyPressed(key, scanCode, modifiers); }
        }
        return true;
    }

    private void moveCursor(int offset) {
        editor.cursor = offset;
        if (!hasShiftDown()) editor.anchor = offset;
        revealCursor();
    }

    private void verticalCursor(int direction) {
        BookPage page = editor.currentPage();
        var lines = lines(page);
        int row = rowAt(lines, editor.cursor);
        int target = Math.clamp(row + direction, 0, lines.size() - 1);
        int pageX = pageX();
        float x = lineX(page, lines.get(row), pageX) + widthBetween(page, lines.get(row).start(), editor.cursor);
        moveCursor(hit(page, pageX, x, textY(page) + target * BookEditor.lineHeight(page) + 1));
    }

    private void insert(String text) {
        try {
            editor.insert(text, this::glyphWidth);
            imageSelected = false;
            changed();
        } catch (RuntimeException exception) {
            status = error(editor.document.kind == DocumentKind.NOTE ? "note_full" : "text");
        }
    }

    /** A layout failure restores the entire change, including its image and formatting. */
    private boolean mutate(Runnable action, boolean paginate) {
        if (!editing()) return false;
        String before = editor.document.json();
        int previousPage = editor.page;
        int previousCursor = editor.cursor;
        editor.remember();
        try {
            action.run();
            if (paginate) editor.paginate(this::glyphWidth);
            BookDocument.parse(editor.document.bytes());
            changed();
            return true;
        } catch (RuntimeException exception) {
            editor.document = BookDocument.parse(before);
            editor.page = previousPage;
            editor.cursor = previousCursor;
            editor.anchor = previousCursor;
            lineCache.clear();
            status = error(editor.document.kind == DocumentKind.NOTE ? "note_full" : "pages");
            return false;
        }
    }

    private void format(int flag, Integer color) {
        mutate(() -> {
            int start = editor.selectionStart();
            int end = editor.selectionEnd();
            if (start == end) {
                start = 0;
                end = editor.currentPage().text().length();
            }
            editor.currentPage().format(start, end, flag, color);
        }, true);
    }

    private void fontSize(int delta) {
        mutate(() -> editor.currentPage().fontSize = Math.clamp(editor.currentPage().fontSize + delta, 9, 14), true);
    }

    private void movePage(int direction) {
        mutate(() -> {
            int target = editor.page + direction;
            java.util.Collections.swap(editor.document.pages, editor.page, target);
            editor.selectPage(target);
        }, false);
    }

    private void deletePage() {
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) mutate(() -> {
                editor.document.pages.remove(editor.page);
                if (editor.document.pages.isEmpty()) editor.document.pages.add(new BookPage());
                editor.selectPage(editor.page);
            }, false);
        }, text("delete_page"), text("undo_hint")));
    }

    private void undo() {
        if (editing() && editor.undo()) {
            imageSelected = false;
            changed();
        }
    }

    private void redo() {
        if (editing() && editor.redo()) {
            imageSelected = false;
            changed();
        }
    }

    private void editTitle() {
        if (editing()) minecraft.setScreen(new BookDialog(this, "books.title", editor.document.title, 80,
                value -> mutate(() -> editor.document.title = value, false)));
    }

    private void searchDialog() {
        minecraft.setScreen(new BookDialog(this, "books.search", search, 256, value -> {
            search = value;
            findNext();
        }));
    }

    private void findNext() {
        if (search.isBlank()) return;
        // regionMatches avoids changing UTF-16 offsets when case conversion expands a character.
        for (int step = 0; step <= editor.document.pages.size(); step++) {
            int index = (editor.page + step) % editor.document.pages.size();
            String value = editor.document.pages.get(index).text();
            int start = step == 0 ? editor.selectionEnd() : 0;
            int end = step == editor.document.pages.size() ? Math.min(editor.selectionEnd(), value.length()) : value.length();
            for (int offset = start; offset + search.length() <= end; offset++) {
                if (value.regionMatches(true, offset, search, 0, search.length())) {
                    selectPage(index);
                    editor.anchor = offset;
                    editor.cursor = offset + search.length();
                    revealCursor();
                    return;
                }
            }
        }
        status = text("not_found");
    }

    private void importImage() {
        if (!editing()) return;
        uploadPage = editor.page;
        minecraft.setScreen(new BookDialog(this));
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (!editing() || paths.isEmpty()) return;
        if (paths.getFirst().getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".cbook")) {
            Path archive = paths.getFirst();
            confirmImport(() -> {
                BookExchangeScreen screen = new BookExchangeScreen(this, false);
                minecraft.setScreen(screen);
                screen.onFilesDrop(List.of(archive));
            });
            return;
        }
        uploadPage = editor.page;
        BookDialog dialog = new BookDialog(this);
        minecraft.setScreen(dialog);
        dialog.importFile(paths.getFirst());
    }

    void uploadImage(byte[] bytes) {
        if (!editing()) return;
        busy = true;
        requestAt = System.currentTimeMillis();
        status = text("uploading");
        BookNetwork.upload(session, UploadKind.IMAGE, bytes);
    }

    private void requestImages() {
        if (localCopy) return;
        for (String hash : editor.document.images()) {
            if (images.get(hash) == null && requestedImages.add(hash)) {
                PacketDistributor.sendToServer(new BookNetwork.Request(session, BookNetwork.Command.IMAGE, hash));
            }
        }
    }

    public void receive(BookNetwork.Image packet) {
        if (!session.equals(packet.session())) return;
        try {
            images.accept(packet);
        } catch (Exception exception) {
            status = error("png_format");
            CivitatumBooks.LOG.warn("Cannot load book image {}", packet.hash(), exception);
        }
    }

    public void receive(BookNetwork.Reply packet) {
        if (!session.equals(packet.session())) return;
        busy = false;
        if (!packet.success()) {
            pendingImport = null;
            saveFailed = true;
            closeAfterSave = false;
            status = error(packet.message());
            backup();
            return;
        }
        if (packet.result() == BookNetwork.Result.IMAGE) {
            if (pendingImport != null) {
                pendingImport.accepted(packet.message());
                continueImport();
                return;
            }
            saveFailed = false;
            editor.selectPage(Math.min(uploadPage, editor.document.pages.size() - 1));
            mutate(() -> {
                BookPage page = editor.currentPage();
                page.image = packet.message();
                BookImages.Image image = images.get(page.image);
                if (image != null) page.imageHeight = Math.clamp((int) (BookEditor.PAGE_WIDTH * page.imageWidth * image.height() / image.width()), 40, 200);
            }, true);
            imageSelected = true;
            return;
        }
        if (packet.result() == BookNetwork.Result.TAKE_FROM_LECTERN) {
            closeNow();
            return;
        }
        // Retain the acknowledged payload, never later unsaved edits in the editor.
        if (capture != null) {
            BookDocument confirmed = BookDocument.parse(sentDocument);
            confirmed.revision = packet.revision();
            confirmed.author = packet.author();
            if (packet.result() == BookNetwork.Result.SIGN) confirmed.signed = true;
            capture.remember(confirmed);
        }
        boolean unchanged = editor.document.json().equals(sentDocument);
        editor.document.revision = packet.revision();
        editor.document.author = packet.author();
        if (packet.result() == BookNetwork.Result.SIGN) {
            editor.document.signed = true;
            editable = administrative;
            changeView(true, false);
            editor.clearHistory();
        }
        dirty = !unchanged;
        saveFailed = false;
        status = dirty ? text("changed") : text("saved");
        backup();
        if (closeAfterSave && !dirty) closeNow();
    }

    private void takeBook() {
        PacketDistributor.sendToServer(new BookNetwork.Request(session, BookNetwork.Command.TAKE_FROM_LECTERN, ""));
    }

    private void editAuthor() {
        if (editing() && administrative) minecraft.setScreen(new BookDialog(this, "books.author", editor.document.author, 80,
                value -> mutate(() -> editor.document.author = value, false)));
    }

    private boolean exportReady() {
        return !busy && editor.document.images().stream().allMatch(hash -> images.get(hash) != null);
    }

    Path defaultArchivePath() {
        String name = editor.document.title.isBlank() ? "book" : editor.document.title;
        name = name.replaceAll("[^\\p{L}\\p{N} _-]", "_").strip();
        return minecraft.gameDirectory.toPath().resolve("civitatum_books/exports").resolve(name + ".cbook").toAbsolutePath();
    }

    BookArchive.Contents archiveSnapshot() {
        return new BookArchive.Contents(editor.document.copy(), images.snapshot());
    }

    void exported(Path path) {
        status = Component.translatable("books.exported", path.getFileName().toString());
    }

    private void confirmImport(Runnable action) {
        if (!editing()) return;
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) action.run();
        }, text("import_book"), text("replace_warning")));
    }

    void importArchive(BookArchive.Contents archive) {
        if (!editing()) return;
        pendingImport = new BookImport(archive, editor.document, administrative);
        continueImport();
    }

    private void continueImport() {
        byte[] image = pendingImport.next();
        if (image != null) {
            uploadImage(image);
            return;
        }
        BookDocument imported = pendingImport.document;
        pendingImport = null;
        boolean applied = mutate(() -> {
            editor.document = imported;
            editor.selectPage(0);
        }, true);
        if (applied) {
            requestImages();
            status = text("imported");
        }
    }

    private void changed() {
        lineCache.clear();
        updateGeometry();
        revealCursor();
        dirty = true;
        saveFailed = false;
        changedAt = System.currentTimeMillis();
        status = text("changed");
    }

    private void confirmSign() {
        if (editor.document.title.isBlank()) {
            minecraft.setScreen(new BookDialog(this, "books.title", "", 80, value -> {
                mutate(() -> editor.document.title = value, false);
                if (!value.isBlank()) confirmSign();
            }));
            return;
        }
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) save(true, false);
        }, text("sign"), text("sign_warning")));
    }

    private void save(boolean sign, boolean closeAfter) {
        if (!editable || busy) return;
        try {
            byte[] bytes = editor.document.bytes();
            BookDocument.parse(bytes);
            backup();
            busy = true;
            requestAt = System.currentTimeMillis();
            closeAfterSave = closeAfter;
            sentDocument = editor.document.json();
            status = text("saving");
            BookNetwork.upload(session, sign ? UploadKind.SIGN : UploadKind.SAVE, bytes);
        } catch (RuntimeException exception) {
            busy = false;
            saveFailed = true;
            status = error("document_size");
        }
    }

    private void backup() {
        if (localCopy) return;
        try {
            drafts.write(editor.document);
            backupAt = System.currentTimeMillis();
        } catch (Exception exception) {
            CivitatumBooks.LOG.warn("Cannot save local book draft {}", book, exception);
        }
    }

    private void recover() {
        if (recovery == null) return;
        mutate(() -> {
            editor.document = recovery;
            editor.selectPage(0);
            recovery = null;
        }, true);
        requestImages();
    }

    @Override
    public void tick() {
        ticks++;
        long now = System.currentTimeMillis();
        if (dirty && now - backupAt >= 2000) backup();
        if (dirty && editable && !busy && !saveFailed && now - changedAt >= 5000) save(false, false);
        if (busy && now - requestAt >= 30000) {
            busy = false;
            pendingImport = null;
            closeAfterSave = false;
            saveFailed = true;
            status = error("timeout");
            backup();
        }
    }

    @Override
    public void onClose() {
        if (busy) return;
        if (dirty && editable) {
            if (saveFailed) {
                backup();
                minecraft.setScreen(new ConfirmScreen(confirmed -> {
                    minecraft.setScreen(this);
                    if (confirmed) closeNow();
                }, text("close_unsaved"), text("local_draft")));
            } else save(false, true);
        } else closeNow();
    }

    void releaseImages() {
        images.close();
    }

    private void closeNow() {
        if (!localCopy) PacketDistributor.sendToServer(new BookNetwork.Request(session, BookNetwork.Command.CLOSE, ""));
        images.close();
        minecraft.setScreen(returnTo);
    }

    @Override
    public void removed() {
        if (dirty) backup();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

