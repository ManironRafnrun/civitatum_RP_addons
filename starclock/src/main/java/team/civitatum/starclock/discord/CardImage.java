package team.civitatum.starclock.discord;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import team.civitatum.starclock.prediction.Constellation;
import team.civitatum.starclock.prediction.Orientation;

/** Лицо карты как в CardScreen, но без надписей: текст идёт в сообщении на двух языках. */
public final class CardImage {
    private static final String TEXTURES = "/assets/starclock/textures/";
    /**
     * CardScreen рисует карту 144×216 и фигуру 124×124 в точке (-62, -67) от центра карты.
     * Текстура карты шире в 256 / 144 = 16 / 9 раза: 124 → 220, -62 → -110, -67 → -119.
     */
    private static final int FIGURE_SIZE = 220;
    private static final int FIGURE_X = -110;
    private static final int FIGURE_Y = -119;

    public static byte[] render(Constellation sign, Orientation orientation) {
        var face = read(sign == Constellation.SERPENT ? "gui/card_zmei" : "gui/card_front");
        var card = new BufferedImage(face.getWidth(), face.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = card.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(face, 0, 0, null);
        if (sign != Constellation.SERPENT) {
            graphics.translate(face.getWidth() / 2, face.getHeight() / 2);
            if (orientation == Orientation.REVERSED) graphics.rotate(Math.PI);
            graphics.drawImage(read("constellation/" + sign.id()), FIGURE_X, FIGURE_Y, FIGURE_SIZE, FIGURE_SIZE, null);
        }
        graphics.dispose();
        return png(card);
    }

    private static BufferedImage read(String texture) {
        try (var stream = CardImage.class.getResourceAsStream(TEXTURES + texture + ".png")) {
            if (stream == null) throw new IllegalStateException("Нет текстуры " + texture);
            return ImageIO.read(stream);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static byte[] png(BufferedImage image) {
        var bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", bytes);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return bytes.toByteArray();
    }

    private CardImage() {}
}
