package team.civitatum.books.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import team.civitatum.books.document.PngImage;
import team.civitatum.books.network.BookNetwork;

final class BookImages {
    record Image(ResourceLocation texture, int width, int height, byte[] png) {}
    private final Map<String, Image> images = new HashMap<>();

    Image get(String hash) {
        return images.get(hash);
    }

    void accept(BookNetwork.Image packet) throws IOException {
        if (images.containsKey(packet.hash()) || !PngImage.hash(packet.data()).equals(packet.hash())) return;
        // The byte[] overload copies the whole PNG onto LWJGL's small thread-local stack.
        // Stream decoding uses a growable native buffer and supports the full upload limit.
        NativeImage pixels = NativeImage.read(new ByteArrayInputStream(packet.data()));
        if (pixels.getWidth() > PngImage.MAX_DIMENSION || pixels.getHeight() > PngImage.MAX_DIMENSION) {
            pixels.close();
            throw new IOException("png_dimensions");
        }
        int width = pixels.getWidth();
        int height = pixels.getHeight();
        ResourceLocation texture = Minecraft.getInstance().getTextureManager().register("civitatum_book", new DynamicTexture(pixels));
        images.put(packet.hash(), new Image(texture, width, height, packet.data().clone()));
    }

    Map<String, byte[]> snapshot() {
        Map<String, byte[]> result = new HashMap<>();
        images.forEach((hash, image) -> result.put(hash, image.png.clone()));
        return Map.copyOf(result);
    }

    void close() {
        var textures = Minecraft.getInstance().getTextureManager();
        images.values().forEach(image -> textures.release(image.texture));
        images.clear();
    }
}
