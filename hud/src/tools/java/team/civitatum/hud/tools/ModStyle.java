package team.civitatum.hud.tools;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;

/**
 * Mods' screens restyled as vanilla's. Every picture of a mod's interface that is a good part grey is recoloured as
 * vanilla's are: greys of any shade laid onto our colours by their brightness, everything else left alone. Not only
 * the big backgrounds: slot sprites, buttons, progress fills, scrollers and panels, of any mod, so no grey is left in
 * a window, whatever its own style. Icons, HUD parts, signs, cursors and flags keep their colours.
 *
 * <p>Screens that use these pictures are found in the bytecode and listed in
 * {@code civitatum_hud/restyled_screens.txt}; there the mod draws dark labels light and the slot highlight in gold.
 * A screen is listed when it names a restyled picture (or one of vanilla's restyled screens) in any of the ways of
 * {@link PictureNames}, or names a class that holds such names, as Create's {@code AllGuiTextures} or Doggy Talents'
 * {@code Resources} do for all their screens. A screen that names no picture at all gets its background from the
 * code that opens it, and is listed when that code names a restyled picture (Ars Elemental's curio bags).
 *
 * <p>Jars inside jars (libraries a mod bundles, such as Create's Ponder) are treated as mods of their own.
 */
final class ModStyle {
    /** Share of vanilla-like greys among the opaque pixels, and the light grey face in pixels, of a vanilla screen. */
    private static final double MIN_GREYS = 0.5;
    private static final int MIN_FACE = 200;
    /** Folders that hold interface pictures besides {@code textures/gui}, as some mods name them (AE2's guis). */
    private static final List<String> INTERFACE_FOLDERS =
        List.of("gui", "guis", "screen", "screens", "container", "menu");
    /** Opaque pixels of a screen background; a smaller picture in vanilla's greys is a button, not a screen. */
    private static final int MIN_SCREEN = 4000;
    /**
     * Screens that show paper with writing on it: books, guides, a notice board, a clipboard. The writing is dark on
     * the paper, and drawing it light would lose it; the screen's own labels stay dark then, the lesser harm. A recipe
     * book is a panel like the others.
     */
    private static final java.util.regex.Pattern PAPER =
        java.util.regex.Pattern.compile("(?i)(?<!recipe)book|bestiary|guide|notice|clipboard|documentation");
    private static final int FACE = 0xFFC6C6C6;
    /**
     * Share of the face and of the slots of {@link VanillaStyle#LIGHT_RAMP} that marks a picture drawn in that lighter
     * palette, when it has less of vanilla's face than of its own.
     */
    private static final double MIN_LIGHT_PALETTE = 0.05;
    /**
     * Palettes of mods that draw their screens in greys of their own, by namespace: each colour and the vanilla grey
     * it stands for. Brightness alone gets them wrong. AE2 draws violet greys, its slots ({@code ADB0C4}) closer to
     * the face ({@code CBCCD4}) than to vanilla's recess, and its sky stone chest is dark with slots lighter than
     * the face.
     */
    private static final Map<String, Map<Integer, Integer>> PALETTES = Map.of("ae2", Map.ofEntries(
        Map.entry(0xFFCBCCD4, 0xFFC6C6C6), Map.entry(0xFFADB0C4, 0xFF8B8B8B), Map.entry(0xFF9A9FB4, 0xFF373737),
        Map.entry(0xFFF2F2F2, 0xFFFFFFFF), Map.entry(0xFF878FA5, 0xFF373737), Map.entry(0xFF413F54, 0xFF000000),
        Map.entry(0xFF696D88, 0xFF555555), Map.entry(0xFF4D4D67, 0xFF373737),
        Map.entry(0xFF2D2F2D, 0xFFC6C6C6), Map.entry(0xFF454D45, 0xFF8B8B8B), Map.entry(0xFF323835, 0xFF373737),
        Map.entry(0xFF575E54, 0xFFFFFFFF), Map.entry(0xFF393E39, 0xFFFFFFFF), Map.entry(0xFF1F2221, 0xFF555555),
        Map.entry(0xFF161717, 0xFF000000)));
    /**
     * The largest side of a small picture: an icon or a button, not a panel. Its cool tints are colours of what it
     * shows (a dragon's scales, an ender eye), not greys of a panel, and what stood out from its ground must still.
     */
    private static final int SMALL = 32;
    /**
     * Brightness apart from the ground of a small picture that marks a drawing on it (an icon on a button), and the
     * least it must keep after recolouring; one that came closer is pushed towards dark or light by that share.
     */
    private static final int STOOD_OUT = 0x20, MIN_CONTRAST = 0x20;
    /**
     * Share of strong colours that makes a small picture a coloured drawing, an icon of something rather than a grey
     * button, see {@link #frame}.
     */
    private static final double MIN_COLOURED = 0.25;
    /** Brightness step between greys of one frame, and the least length of a frame, see {@link #frame}. */
    private static final int FRAME_STEP = 0x30, MIN_FRAME = 12;
    /**
     * Share of strong colours in a patch on a small button's face that makes it an item's picture (Waystones'
     * sharestone and ender eye): the item keeps its own colours, its grey stone too, only the button around it is
     * restyled, see {@link #item}. Counted within the patch: a wide white bevel would thin it out.
     */
    private static final double MIN_ITEM_COLOURS = 0.1;
    /** Channels of a near grey of a small picture may differ this much, warm ones too (MineColonies' C1BBBA). */
    private static final int NEAR_GREY = 12;
    /**
     * How much wider a light plate's shading is spread from its lightest grey (MineColonies' pale research buttons, a
     * white glyph with grey shading): the icons' ramp halves differences, this gives back most of them.
     */
    private static final double PLATE_STRETCH = 1.6;
    /** Share of a coloured drawing's grey ground that makes it a button's face (Curios' bow on a vanilla button). */
    private static final double MIN_DRAWING_GROUND = 0.25;
    /** Blue over red and over green of a hover's light, pale lilac included; share of it a plain sibling may have. */
    private static final int HOVER_TINT = 8;
    private static final double MAX_PLAIN_BLUE = 0.05;
    private static final double CONTRAST_PUSH = 0.75;
    /** Width of a small button's outline and bevel along its edge, see {@link #keepContrast}. */
    private static final int BEVEL = 3;
    /**
     * Least share of the ground, and most of a colour pushed off it: a drawing is small on a clear ground. A picture
     * of a few large areas (a plain button) keeps them as recoloured.
     */
    private static final double MIN_GROUND = 0.4, MAX_DRAWING = 0.25;
    /**
     * A small picture with this share of strong cool colours (of this saturation) is a coloured drawing, a blue
     * dragon or an ender eye: its cool greys are shades of those colours and stay. One without is a grey thing with a
     * cool cast (FTB Quests' chain, Moonlight's chat bubble, MineColonies' lavender white button), recoloured whole.
     * Deciding for the whole picture keeps a gradient of tints from splitting into two colours.
     */
    private static final double MIN_COOL_DRAWING = 0.1, STRONG_COOL = 0.35;
    /** Brightest channel of a colour too dark for its saturation to tell: Moonlight's near black outline. */
    private static final int DARKEST_COLOUR = 0x50;
    /** Brightness of a ground too dark to be one: the outline of a picture made mostly of its outline. */
    private static final int DARKEST_GROUND = 0x20;
    /**
     * Brightness of a ground too light to be a button's face: a white glyph (an arrow, a scroll thumb) is all drawing,
     * and goes onto the icons' ramp whole, its most common white with the rest.
     */
    private static final int LIGHTEST_GROUND = 0xD0;
    /** Pictures of a hovered or selected button, and the share of blue that makes the blue all theirs. */
    private static final java.util.regex.Pattern HOVER =
        java.util.regex.Pattern.compile("(?i)hover|highlight|selected");
    private static final double MIN_HOVER_BLUE = 0.5;
    private static final int LIGHT_FACE = 0xE4, LIGHT_SLOT = 0xC3, LIGHT_TOLERANCE = 3, LIGHT_SPREAD = 2;
    /** Share of greys of any shade that makes a picture part of a screen, to be recoloured. */
    private static final double MIN_BACKING = 0.15;
    /**
     * Share of a picture that the recolouring must darken for its screens to be listed, in a mod with no screen in
     * vanilla's style, and the picture must be as big as a screen's background ({@link #MIN_SCREEN}): a dark panel
     * then, not paper with a grey button or ornament on it, where light labels would be lost.
     */
    private static final double MIN_DARKENED = 0.5;
    /**
     * Parts of paths of pictures that are no screen's background: icons, HUD, signs, cursors, title screens,
     * panoramas and logos, banners and drawings, books, whose dark text is written on their paper (the recipe book is
     * a screen like the others), flags, brand logos of social media, and pictures of items and hearts that mods keep
     * outside their icon folders. And colour pickers, whose greys are the colours they offer.
     */
    private static final List<String> NOT_SCREEN = List.of("icon", "hud", "abilities", "penalties", "hanging_signs",
        "cursors", "panorama", "title/", "main_menu", "logo", "banner", "patreon", "drawing", "bestiary", "book",
        "flags/", "media/", "professions/", "personalities/", "heart", "color");
    /**
     * Pictures that may be hints of empty slots, by their path within {@code textures/}: Curios', Accessories' and
     * the Aether's slot icons, Moonlight's for the cartography table, Supplementaries' for the pulley and the loom,
     * Farmer's Delight's bowl for the cooking pot, TrashSlot's can in its cell. What they show decides, see
     * {@link VanillaStyle#isHint}.
     */
    private static final java.util.regex.Pattern HINT_PATH =
        java.util.regex.Pattern.compile("(?i)slot|empty|hint|trashcan");
    /** Folders of the world's textures, whose pictures are never slot hints. */
    private static final List<String> WORLD_FOLDERS = List.of("block", "entity", "particle", "models", "armor");
    static final Path SCREENS = Path.of("src/main/resources/civitatum_hud/restyled_screens.txt");

    private final VanillaStyle style;
    private final TreeSet<String> screens = new TreeSet<>();
    /** Jars found inside jars, done after all the mods themselves. */
    private final Map<String, byte[]> bundled = new LinkedHashMap<>();
    /** Pictures written so far: a mod's own copy wins over an older one bundled in another mod. */
    private final Set<String> written = new HashSet<>();
    private final List<String> report = new ArrayList<>();
    /** The pictures of the jar being restyled, for the plain sibling of a hovered one. */
    private Map<String, byte[]> jarPictures = Map.of();

    ModStyle(VanillaStyle style) {
        this.style = style;
    }

    void run(Path mods) throws IOException {
        List<Path> jars;
        try (var files = Files.list(mods)) {
            jars = files.filter(f -> f.toString().endsWith(".jar")).sorted().toList();
        }
        for (Path jar : jars) {
            try (InputStream in = Files.newInputStream(jar)) {
                restyle(jar.getFileName().toString(), in);
            }
        }
        while (!bundled.isEmpty()) {
            var inner = bundled.entrySet().iterator().next();
            bundled.remove(inner.getKey());
            restyle(inner.getKey(), new ByteArrayInputStream(inner.getValue()));
        }
        var lines = new ArrayList<String>();
        lines.add("# Screens drawn on restyled pictures, written by ./gradlew :hud:generateVanillaStyle.");
        lines.add("# Their dark labels are drawn light and slot highlights in gold. One class name per line.");
        lines.addAll(screens);
        Files.createDirectories(SCREENS.getParent());
        Files.write(SCREENS, lines, StandardCharsets.UTF_8);
        report.forEach(System.out::println);
        System.out.println(screens.size() + " screens listed in " + SCREENS);
    }

    /** One jar: its pictures, then its screens; the jars bundled in it wait for their turn. */
    private void restyle(String jar, InputStream in) throws IOException {
        var pictures = new LinkedHashMap<String, byte[]>();
        var metadata = new HashMap<String, byte[]>();
        var hints = new LinkedHashMap<String, byte[]>();
        var classes = new HashMap<String, byte[]>();
        var zip = new ZipInputStream(in);
        for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
            String name = entry.getName();
            if (interfacePicture(name)) {
                byte[] bytes = zip.readAllBytes();
                pictures.put(name, bytes);
                if (hintPicture(name)) hints.put(name, bytes);
            } else if (hintPicture(name)) hints.put(name, zip.readAllBytes());
            else if (name.endsWith(".png.mcmeta")) metadata.put(name, zip.readAllBytes());
            else if (name.endsWith(".class")) classes.put(name.substring(0, name.length() - ".class".length()),
                zip.readAllBytes());
            else if (name.endsWith(".jar")) bundled.put(jar + "!" + name, zip.readAllBytes());
        }
        jarPictures = pictures;
        int hinted = restyleHints(hints);
        if (hinted > 0) report.add(hinted + " slot hints from " + jar);
        var images = new LinkedHashMap<String, BufferedImage>();
        var backgrounds = new HashSet<String>();
        var lightPalette = new HashSet<String>();
        var styled = new HashSet<String>();
        for (var picture : pictures.entrySet()) {
            BufferedImage image;
            try {
                image = ImageIO.read(new ByteArrayInputStream(picture.getValue()));
            } catch (IOException ex) {
                continue;
            }
            if (image == null) continue;
            var greys = Greys.of(image);
            if (greys.screen()) styled.add(namespace(picture.getKey()));
            if (greys.visible() >= MIN_SCREEN) backgrounds.add(picture.getKey());
            if (greys.lightPalette()) lightPalette.add(picture.getKey());
            boolean part = (greys.backing() && !greys.fill() || blueHover(picture.getKey(), image))
                && partOfScreen(picture.getKey());
            if (greys.screen() || part) {
                images.put(picture.getKey(), image);
            }
        }
        // what classes may name: this mod's restyled pictures, and vanilla's screens
        var names = new PictureNames();
        for (String name : VanillaStyle.CONTAINERS) {
            names.add("minecraft", "textures/gui/container/" + name + ".png", false);
        }
        int restyled = 0;
        for (var picture : images.entrySet()) {
            String path = picture.getKey();
            double darkened = recolour(path, picture.getValue(), metadata, lightPalette.contains(path));
            if (darkened == 0) continue;
            restyled++;
            if (styled.contains(namespace(path)) || darkened >= MIN_DARKENED && backgrounds.contains(path)) {
                names.add(namespace(path), path.substring(path.indexOf("/textures/") + 1), true);
            }
        }
        if (restyled > 0) report.add(restyled + " pictures from " + jar);
        findScreens(classes, names);
    }

    /** Whether a picture is a hovered or selected button, blue for the most part: it turns gold, as vanilla's does. */
    private static boolean blueHover(String path, BufferedImage image) {
        String file = path.substring(path.lastIndexOf('/') + 1);
        return HOVER.matcher(file).find() && blueShare(image) >= MIN_HOVER_BLUE;
    }

    /**
     * Pixels of a small picture that stood out from its ground (its most common colour) and were recoloured into it
     * get their difference back: darker ones towards the outline, lighter ones towards the lit gold. Otherwise a grey
     * icon on a hovered button's blue turns as brown as the button. They get as much as they had, no more: a dim plus
     * on Stellaris' dark button stays dim. A strong colour left as it was is told by its hue and stays (Dragon
     * Survival's green).
     */
    private static void keepContrast(BufferedImage image, BufferedImage out) {
        var counts = new HashMap<Integer, Integer>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if (argb >>> 24 == 0xFF) counts.merge(argb, 1, Integer::sum);
            }
        }
        if (counts.isEmpty()) return;
        int ground = counts.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        int opaque = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (counts.get(ground) < MIN_GROUND * opaque || brightness(ground) < DARKEST_GROUND) return;
        long sum = 0;
        int n = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != ground) continue;
                sum += brightness(out.getRGB(x, y));
                n++;
            }
        }
        int was = brightness(ground), now = (int) (sum / n);
        // a light drawing on the ground (a white glyph) makes the dark around it its outline, which stays dark; the
        // light bevel along a button's edge is no drawing
        boolean lightDrawing = false;
        for (int y = BEVEL; y < image.getHeight() - BEVEL && !lightDrawing; y++) {
            for (int x = BEVEL; x < image.getWidth() - BEVEL; x++) {
                int argb = image.getRGB(x, y);
                if (argb >>> 24 == 0xFF && brightness(argb) - was >= STOOD_OUT) lightDrawing = true;
            }
        }
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if (argb >>> 24 != 0xFF || argb == ground || counts.get(argb) > MAX_DRAWING * opaque) continue;
                if (out.getRGB(x, y) == argb && saturation(argb) >= STRONG_COOL) continue;
                int apart = brightness(argb) - was, recoloured = out.getRGB(x, y);
                if (Math.abs(apart) < STOOD_OUT || Math.abs(brightness(recoloured) - now) >= MIN_CONTRAST) continue;
                // keep the drawing darker or lighter than its ground, unless our dark ground leaves no room for darker
                int towards = apart < 0 ? VanillaStyle.OUTLINE : VanillaStyle.LIT_GOLD;
                if (apart < 0 && now - brightness(VanillaStyle.OUTLINE) < MIN_CONTRAST) {
                    if (lightDrawing) continue;
                    towards = VanillaStyle.LIT_GOLD;
                }
                int target = now + Integer.signum(brightness(towards) - now) * Math.max(Math.abs(apart), MIN_CONTRAST);
                double way = brightness(towards) - brightness(recoloured);
                double push = way == 0 ? 0 : Math.min(CONTRAST_PUSH, (target - brightness(recoloured)) / way);
                if (push > 0) out.setRGB(x, y, VanillaStyle.mix(recoloured, towards, push));
            }
        }
    }

    /**
     * The plain sibling of a hovered picture ({@code back_page} for {@code back_page_hover}) when it has no blue of
     * its own, or null: then what is blue or lilac in the hovered one where the plain one differs is the hover's
     * light (Stellaris' icons, Polymorph's and Waystones' buttons), and turns to the lit gold. What both share (Curios'
     * purple bow) is the picture's own. A blue dragon has no plain sibling and keeps its blue.
     */
    private BufferedImage plainSibling(String path, String file) {
        if (!HOVER.matcher(file).find()) return null;
        String plain = path.replaceAll("_(hovered|hover|highlighted|highlight)(?=\\.png$)", "");
        byte[] bytes = jarPictures.get(plain);
        if (plain.equals(path) || bytes == null) return null;
        try {
            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) return null;
            int visible = 0, tinted = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    if (argb >>> 24 != 0xFF) continue;
                    visible++;
                    if (hoverTint(argb)) tinted++;
                }
            }
            return visible > 0 && tinted < MAX_PLAIN_BLUE * visible ? image : null;
        } catch (IOException ex) {
            return null;
        }
    }

    /** Whether a pixel of a hovered picture is the hover's light: blue or lilac where its plain sibling is not. */
    private static boolean hoverLight(BufferedImage image, BufferedImage plain, int x, int y, int shifted) {
        boolean sameSize = plain.getWidth() == image.getWidth() && plain.getHeight() == image.getHeight();
        return hoverTint(shifted) && (!sameSize || plain.getRGB(x, y) != image.getRGB(x, y));
    }

    /**
     * Whether a pixel of a hovered button is its dark blue shade over a grey of the plain one (Waystones' 293947 over
     * 373737): too dark for a hover's light, it takes the plain one's grey, restyled as the hovered button's.
     */
    private static boolean hoverShade(BufferedImage plain, int x, int y, int shifted) {
        if (plain.getWidth() <= x || plain.getHeight() <= y) return false;
        int under = plain.getRGB(x, y), r = shifted >> 16 & 0xFF, g = shifted >> 8 & 0xFF, b = shifted & 0xFF;
        return under != shifted && under >>> 24 == 0xFF && VanillaStyle.neutral(under)
            && b > r && b >= g && brightest(shifted) < DARKEST_COLOUR;
    }

    /** Whether the plain sibling of a hovered picture is grey, or not there, at {@code x, y}. */
    private static boolean overGrey(BufferedImage plain, int x, int y) {
        if (plain.getWidth() <= x || plain.getHeight() <= y) return true;
        int under = plain.getRGB(x, y);
        return under >>> 24 != 0xFF || VanillaStyle.neutral(under);
    }

    /**
     * Whether a colour is blue or lilac at all, pale ones included ({@link #HOVER_TINT}); a near black outline with a
     * cool cast is not, and neither is purple (red over green), a drawing's colour.
     */
    private static boolean hoverTint(int argb) {
        int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
        return b - r >= HOVER_TINT && b - g >= HOVER_TINT && g >= r && b >= DARKEST_COLOUR;
    }

    /**
     * The frame of a coloured drawing: greys joined to each other at a like brightness ({@link #FRAME_STEP}) into a
     * run of {@link #MIN_FRAME} pixels or more that reaches the picture's edge or its transparency, as a dragon's white
     * outline does, and the run of the picture's grey ground (a button's face under a bow). Greys within the drawing
     * (the white play sign of a logo, an awning's stripes, a flame's hot core) are its shading and light: they go onto
     * the icons' ramp, in order of brightness, up to the lit gold.
     */
    private static boolean[][] frame(java.util.function.IntBinaryOperator pixels, int width, int height, int ground) {
        var frame = new boolean[height][width];
        var seen = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (seen[y][x] || !grey(pixels, x, y)) continue;
                var run = new ArrayList<int[]>();
                var queue = new java.util.ArrayDeque<int[]>();
                queue.add(new int[]{x, y});
                seen[y][x] = true;
                boolean outside = false;
                while (!queue.isEmpty()) {
                    int[] at = queue.poll();
                    run.add(at);
                    int level = brightness(pixels.applyAsInt(at[0], at[1]));
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = at[0] + dx, ny = at[1] + dy;
                            if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                                outside = true;
                                continue;
                            }
                            if (pixels.applyAsInt(nx, ny) >>> 24 == 0) outside = true;
                            if (seen[ny][nx] || !grey(pixels, nx, ny)) continue;
                            if (Math.abs(brightness(pixels.applyAsInt(nx, ny)) - level) > FRAME_STEP) continue;
                            seen[ny][nx] = true;
                            queue.add(new int[]{nx, ny});
                        }
                    }
                }
                boolean onGround = run.stream().anyMatch(at -> pixels.applyAsInt(at[0], at[1]) == ground);
                if (!onGround && (!outside || run.size() < MIN_FRAME)) continue;
                for (int[] at : run) frame[at[1]][at[0]] = true;
            }
        }
        return frame;
    }

    /**
     * The item drawn on a small button's face ({@link #MIN_ITEM_COLOURS}), null when there is none: the patches off
     * the face that hold strong colours and keep off the button's edge. The bevel touches the edge, a white glyph
     * beside the item (Waystones' spiral) holds no colour, and a hover's blue is no item's colour; they are restyled.
     */
    private static boolean[][] item(java.util.function.IntBinaryOperator pixels, int width, int height, int ground) {
        var item = new boolean[height][width];
        var seen = new boolean[height][width];
        boolean any = false;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = pixels.applyAsInt(x, y);
                if (seen[y][x] || !inItem(argb, ground)) continue;
                var patch = new ArrayList<int[]>();
                var queue = new java.util.ArrayDeque<int[]>();
                queue.add(new int[]{x, y});
                seen[y][x] = true;
                boolean edge = false;
                int strong = 0;
                while (!queue.isEmpty()) {
                    int[] at = queue.poll();
                    patch.add(at);
                    int colour = pixels.applyAsInt(at[0], at[1]);
                    if (saturation(colour) >= STRONG_COOL && brightest(colour) >= DARKEST_COLOUR
                        && !VanillaStyle.anyBlue(colour) && !VanillaStyle.vanillaHover(colour)) strong++;
                    for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = at[0] + step[0], ny = at[1] + step[1];
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height
                            || pixels.applyAsInt(nx, ny) >>> 24 != 0xFF) {
                            edge = true;
                            continue;
                        }
                        if (seen[ny][nx] || !inItem(pixels.applyAsInt(nx, ny), ground)) continue;
                        seen[ny][nx] = true;
                        queue.add(new int[]{nx, ny});
                    }
                }
                if (edge || strong < MIN_ITEM_COLOURS * patch.size()) continue;
                any = true;
                for (int[] at : patch) item[at[1]][at[0]] = true;
            }
        }
        return any ? item : null;
    }

    /**
     * Whether a pixel may be a part of an item on a button's face: not the face, and not white, which is the
     * picture's light (the label of Waystones' floppy disk) and goes gold as any icon's does.
     */
    private static boolean inItem(int argb, int ground) {
        boolean white = VanillaStyle.neutral(argb) && brightness(argb) > LIGHTEST_GROUND;
        return argb >>> 24 == 0xFF && argb != ground && !white;
    }

    /** Whether a colour covers so much of a picture ({@code share}) that it is the ground the drawing is on. */
    private static boolean clearGround(BufferedImage image, int colour, double share) {
        int opaque = 0, count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if (argb >>> 24 != 0xFF) continue;
                opaque++;
                if (argb == colour) count++;
            }
        }
        return opaque > 0 && count >= share * opaque;
    }

    private static boolean grey(java.util.function.IntBinaryOperator pixels, int x, int y) {
        int argb = pixels.applyAsInt(x, y);
        return argb >>> 24 == 0xFF && VanillaStyle.neutral(argb);
    }

    /**
     * The most common opaque colour of a picture no brighter than {@code lightest}, its ground; 0 when it has none.
     * Its outline is not, however much of a small picture it takes ({@link #DARKEST_GROUND}).
     */
    private static int ground(java.util.function.IntBinaryOperator pixels, int width, int height, int lightest) {
        var counts = new HashMap<Integer, Integer>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = pixels.applyAsInt(x, y);
                int level = brightness(argb);
                if (argb >>> 24 == 0xFF && level >= DARKEST_GROUND && level <= lightest) {
                    counts.merge(argb, 1, Integer::sum);
                }
            }
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0);
    }

    /** The share of a picture's visible pixels in strong colours, see {@link #MIN_COLOURED}. */
    private static double strongShare(BufferedImage image) {
        int visible = 0, coloured = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if (argb >>> 24 == 0) continue;
                visible++;
                if (saturation(argb) >= STRONG_COOL && brightest(argb) >= DARKEST_COLOUR) coloured++;
            }
        }
        return visible == 0 ? 0 : coloured / (double) visible;
    }

    /** Whether a colour of a small picture is near enough a grey to be one, warm or not ({@link #NEAR_GREY}). */
    private static boolean nearGrey(int argb) {
        int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= NEAR_GREY;
    }

    /** The lightest grey of a picture, the top of its plate's shading. */
    private static int lightestGrey(java.util.function.IntBinaryOperator pixels, int width, int height) {
        int lightest = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = pixels.applyAsInt(x, y);
                if (argb >>> 24 == 0xFF && (VanillaStyle.neutral(argb) || nearGrey(argb))) {
                    lightest = Math.max(lightest, brightness(argb));
                }
            }
        }
        return lightest;
    }

    /** A grey of a light plate set {@link #PLATE_STRETCH} times further off its lightest, which turns white. */
    private static int stretched(int argb, int lightest) {
        int level = Math.max(0, (int) Math.round(0xFF - (lightest - brightness(argb)) * PLATE_STRETCH));
        return argb & 0xFF000000 | Math.min(0xFF, level) * 0x010101;
    }

    /** Whether a small picture is a coloured drawing in cool colours, see {@link #MIN_COOL_DRAWING}. */
    private static boolean coolDrawing(BufferedImage image) {
        int visible = 0, cool = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if (argb >>> 24 == 0) continue;
                visible++;
                int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
                boolean bright = brightest(argb) >= DARKEST_COLOUR;
                if (bright && saturation(argb) >= STRONG_COOL && (g > r || b > r)) cool++;
            }
        }
        return visible > 0 && cool >= MIN_COOL_DRAWING * visible;
    }

    private static int brightest(int argb) {
        return Math.max(argb >> 16 & 0xFF, Math.max(argb >> 8 & 0xFF, argb & 0xFF));
    }

    private static double saturation(int argb) {
        int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF, max = Math.max(r, Math.max(g, b));
        return max == 0 ? 0 : (max - Math.min(r, Math.min(g, b))) / (double) max;
    }

    /** Perceived brightness of an opaque colour, 0 to 255. */
    private static int brightness(int argb) {
        return ((argb >> 16 & 0xFF) * 3 + (argb >> 8 & 0xFF) * 6 + (argb & 0xFF)) / 10;
    }

    /** The share of a picture's visible pixels that are blue, see {@link VanillaStyle#anyBlue}. */
    private static double blueShare(BufferedImage image) {
        int visible = 0, blue = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if (argb >>> 24 == 0) continue;
                visible++;
                if (VanillaStyle.anyBlue(argb)) blue++;
            }
        }
        return visible == 0 ? 0 : blue / (double) visible;
    }

    /** Writes the hints of empty slots among the pictures in the loom's style; returns how many there were. */
    private int restyleHints(Map<String, byte[]> hints) throws IOException {
        int count = 0;
        for (var hint : hints.entrySet()) {
            if (written.contains(hint.getKey())) continue;
            BufferedImage image;
            try {
                image = ImageIO.read(new ByteArrayInputStream(hint.getValue()));
            } catch (IOException ex) {
                continue;
            }
            if (image == null || !VanillaStyle.isHint(image)) continue;
            Path target = VanillaStyle.PACK.resolve(hint.getKey());
            Files.createDirectories(target.getParent());
            ImageIO.write(style.hint(image), "png", target.toFile());
            written.add(hint.getKey());
            count++;
        }
        return count;
    }

    /**
     * Writes the picture recoloured, with its metadata; one drawn in the {@code light} palette or in a mod's own
     * ({@link #PALETTES}) is first shifted to vanilla's. Returns the share of its visible pixels that changed, 0 when
     * it has no grey to swap; 1 when a mod written earlier had the picture, which is then left as it was written.
     */
    private double recolour(String path, BufferedImage image, Map<String, byte[]> metadata, boolean light)
            throws IOException {
        if (written.contains(path)) return 1;
        String file = path.substring(path.lastIndexOf('/') + 1, path.length() - ".png".length());
        var out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int visible = 0, changed = 0, width = image.getWidth(), height = image.getHeight();
        // a mod's palette tells the parts of its screens; on a small sprite (AE2's scroll thumb) a colour may play
        // another part, and the order of brightness alone keeps its depth
        var palette = Math.max(width, height) <= SMALL ? Map.<Integer, Integer>of()
            : PALETTES.getOrDefault(namespace(path), Map.of());
        boolean hoverBlue = blueHover(path, image);
        java.util.function.IntBinaryOperator pixels = (px, py) -> {
            int argb = image.getRGB(px, py);
            Integer own = palette.get(argb | 0xFF000000);
            if (own != null) return argb & 0xFF000000 | own & 0xFFFFFF;
            return light ? VanillaStyle.vanillaFromLight(argb) : argb;
        };
        boolean[][] slots = VanillaStyle.slots(pixels, width, height);
        boolean small = Math.max(width, height) <= SMALL;
        BufferedImage plain = plainSibling(path, file);
        boolean hoverOver = plain != null;
        String palette2 = hoverOver ? file + "_highlighted" : file;
        // a hover's lilac light is no drawing's colour: the icon under it is recoloured whole, its pale lilac too
        boolean coolDrawing = small && !hoverOver && coolDrawing(image);
        // a coloured blue button (Structurize's) keeps its blues; a hovered one's blue is still the hover's
        boolean ownBlues = coolDrawing && !HOVER.matcher(file).find();
        int ground = small ? ground(pixels, width, height, 0xFF) : 0;
        boolean faced = clearGround(image, ground, MIN_DRAWING_GROUND);
        // a hovered button's white bevel may outnumber its face (Waystones' sharestone)
        int face = small ? ground(pixels, width, height, LIGHTEST_GROUND) : 0;
        boolean[][] item = small && file.contains("button") && clearGround(image, face, MIN_DRAWING_GROUND)
            ? item(pixels, width, height, face) : null;
        boolean[][] frame = small && (item != null || !hoverOver && strongShare(image) >= MIN_COLOURED)
            ? frame(pixels, width, height, faced ? ground : 0) : null;
        // a light plate or a white glyph: its shading spread wider, so the icons' ramp keeps its depth
        boolean lightPlate = small && frame == null && brightness(ground) > LIGHTEST_GROUND
            && clearGround(image, ground, MIN_GROUND);
        int lightest = lightPlate ? lightestGrey(pixels, width, height) : 0xFF;
        style.flat(small);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y), detail = style.detail(pixels, width, height, x, y, slots);
                int shifted = pixels.applyAsInt(x, y);
                boolean opaque = shifted >>> 24 == 0xFF, neutral = VanillaStyle.neutral(shifted);
                int recoloured;
                if (detail != 0) recoloured = detail;
                else if (opaque && (hoverOver ? hoverLight(image, plain, x, y, shifted)
                    : hoverBlue && VanillaStyle.anyBlue(shifted))) {
                    recoloured = style.litBlue(shifted, x, y, file);
                } else if (opaque && hoverOver && hoverShade(plain, x, y, shifted)) {
                    recoloured = style.recolour(plain.getRGB(x, y), x, y, palette2);
                } else if (ownBlues && !neutral && !VanillaStyle.vanillaHover(shifted)) recoloured = shifted;
                else if (item != null && item[y][x]) recoloured = shifted;
                else if (frame != null && neutral && !frame[y][x]) recoloured = style.iconGrey(shifted, true);
                else if (small && coolDrawing && VanillaStyle.coolGrey(shifted)) recoloured = shifted;
                else if (small && frame == null && opaque && (neutral || nearGrey(shifted))
                    && (shifted != ground || brightness(ground) > LIGHTEST_GROUND)) {
                    int grey = lightPlate ? stretched(shifted, lightest) : shifted;
                    // a hover's light over the mod's own warm drawing (Ars Zero's cream arrow) is no lit button
                    recoloured = style.iconGrey(grey, hoverOver && overGrey(plain, x, y));
                } else recoloured = style.recolour(shifted, x, y, palette2);
                if (argb >>> 24 != 0) visible++;
                if (recoloured != argb) changed++;
                out.setRGB(x, y, recoloured);
            }
        }
        style.flat(false);
        if (changed == 0) return 0;
        if (small) keepContrast(image, out);
        written.add(path);
        Path target = VanillaStyle.PACK.resolve(path);
        Files.createDirectories(target.getParent());
        ImageIO.write(out, "png", target.toFile());
        byte[] meta = metadata.get(path + ".mcmeta");
        if (meta != null) Files.write(VanillaStyle.PACK.resolve(path + ".mcmeta"), meta);
        return changed / (double) visible;
    }

    /** Screens naming a restyled picture; other classes naming one hold the names for the screens that use them. */
    private void findScreens(Map<String, byte[]> classes, PictureNames names) {
        var parents = new HashMap<String, String>();
        var constants = new HashMap<String, String>();
        for (var type : classes.entrySet()) {
            String parent = ClassNames.parent(type.getValue());
            if (parent != null) parents.put(type.getKey(), parent);
            constants.put(type.getKey(), new String(type.getValue(), StandardCharsets.ISO_8859_1));
        }
        var holders = new ArrayList<String>();
        for (var type : classes.entrySet()) {
            if (!names.namedBy(ClassNames.strings(type.getValue()))) continue;
            if (screen(type.getKey(), parents)) list(type.getKey());
            else holders.add(type.getKey());
        }
        for (var type : classes.keySet()) {
            String text = constants.get(type);
            if (!screen(type, parents)) continue;
            if (holders.stream().anyMatch(text::contains)) list(type);
            // a screen naming no picture at all is handed its background by whoever opens it: a holder naming it
            else if (!text.contains("textures/") && !text.contains(".png")
                    && holders.stream().anyMatch(h -> constants.get(h).contains(type))) {
                list(type);
            }
        }
    }

    /** Lists a screen, unless its name or package tells of paper (Ars Nouveau's {@code documentation}). */
    private void list(String type) {
        if (!PAPER.matcher(type).find()) screens.add(type.replace('/', '.'));
    }

    /**
     * Whether the class is a screen: its parents, within the mod, lead to Minecraft's screens, or to a screen of a
     * library outside the mod, which is only known by its name (Let's Do's {@code StationRecipeBookScreen}).
     */
    private static boolean screen(String type, Map<String, String> parents) {
        for (String parent = parents.get(type); parent != null; parent = parents.get(parent)) {
            if (parent.startsWith("net/minecraft/client/gui/screens/")) return true;
            if (!parents.containsKey(parent) && !parent.startsWith("java/")) {
                return parent.endsWith("Screen") || parent.endsWith("Gui");
            }
        }
        return false;
    }

    private static String namespace(String path) {
        return path.split("/")[1];
    }

    /**
     * A picture of a mod's interface; vanilla's own are restyled by {@link VanillaStyle}. HUD pictures are drawn over
     * the world, not on a screen, and keep their look.
     */
    private static boolean interfacePicture(String path) {
        if (!path.startsWith("assets/") || path.startsWith("assets/minecraft/") || !path.endsWith(".png")) return false;
        int textures = path.indexOf("/textures/");
        if (textures < 0) return false;
        String inTextures = path.substring(textures + "/textures/".length());
        if (inTextures.contains("hud")) return false;
        // by folder, not by file name: a block of the world may well be called a screen
        var folders = List.of(inTextures.substring(0, Math.max(0, inTextures.lastIndexOf('/'))).split("/"));
        return folders.getFirst().equals("gui") || folders.stream().anyMatch(INTERFACE_FOLDERS::contains);
    }

    /** A mod's picture that may be a hint of an empty slot, by its path ({@link #HINT_PATH}); a button never is. */
    private static boolean hintPicture(String path) {
        if (!path.startsWith("assets/") || path.startsWith("assets/minecraft/") || !path.endsWith(".png")) return false;
        int textures = path.indexOf("/textures/");
        if (textures < 0) return false;
        String inTextures = path.substring(textures + "/textures/".length());
        return HINT_PATH.matcher(inTextures).find() && !inTextures.contains("button")
            && !WORLD_FOLDERS.contains(inTextures.split("/")[0]);
    }

    /** Whether no folder or name on the picture's path marks it as an icon, a HUD part, a sign or a cursor. */
    private static boolean partOfScreen(String path) {
        String inTextures = path.substring(path.indexOf("/textures/") + "/textures/".length());
        String parts = inTextures.replace("recipe_book", "recipes");
        return NOT_SCREEN.stream().noneMatch(parts::contains);
    }

    /**
     * Opaque pixels of a picture, those of vanilla's greys and the face alone among them; visible pixels, and those of
     * any grey and of white among them.
     */
    private record Greys(int opaque, int greys, int face, int visible, int neutral, int light, int exactFace,
            int lightFace, int lightSlot) {
        static Greys of(BufferedImage image) {
            int opaque = 0, greys = 0, face = 0, visible = 0, neutral = 0, light = 0;
            int exactFace = 0, lightFace = 0, lightSlot = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    if (argb >>> 24 == 0) continue;
                    visible++;
                    if (VanillaStyle.neutral(argb)) neutral++;
                    if ((argb & 0xFFFFFF) == 0xFFFFFF) light++;
                    if (argb >>> 24 != 0xFF) continue;
                    opaque++;
                    int grey = VanillaStyle.vanillaGrey(argb);
                    if (grey != 0) greys++;
                    if (grey == FACE) face++;
                    int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
                    if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > LIGHT_SPREAD) continue;
                    if (Math.abs(g - (FACE & 0xFF)) <= LIGHT_SPREAD) exactFace++;
                    if (Math.abs(g - LIGHT_FACE) <= LIGHT_TOLERANCE) lightFace++;
                    if (Math.abs(g - LIGHT_SLOT) <= 1) lightSlot++;
                }
            }
            return new Greys(opaque, greys, face, visible, neutral, light, exactFace, lightFace, lightSlot);
        }

        /** A screen background in vanilla's style: mostly its greys, with a real face. */
        boolean screen() {
            return opaque >= MIN_SCREEN && face >= MIN_FACE && greys >= MIN_GREYS * opaque;
        }

        /**
         * A plain white picture, all of it light: the mod tints it at run time to fill shapes of any colour (Veil's
         * {@code white.png}), and gold there would tint them all.
         */
        boolean fill() {
            return visible > 0 && light == visible;
        }

        /** A screen in the lighter palette of {@link VanillaStyle#LIGHT_RAMP}: its face and slots, few of vanilla's. */
        boolean lightPalette() {
            return lightFace >= MIN_LIGHT_PALETTE * opaque && lightSlot >= MIN_LIGHT_PALETTE * opaque
                && exactFace < lightFace;
        }

        /** A sprite drawn on such a screen, a slot, a fill, a scroller or a panel: a good part of it grey. */
        boolean backing() {
            return visible > 0 && neutral >= MIN_BACKING * visible;
        }
    }
}
