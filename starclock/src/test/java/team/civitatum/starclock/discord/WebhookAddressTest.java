package team.civitatum.starclock.discord;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WebhookAddressTest {
    @TempDir
    Path folder;

    @Test
    void missingFileDisablesSending() throws Exception {
        assertEquals("", WebhookAddress.read(folder.resolve(WebhookAddress.FILE_NAME)));
    }

    @Test
    void blankFileDisablesSending() throws Exception {
        var file = Files.writeString(folder.resolve(WebhookAddress.FILE_NAME), " \r\n\n");
        assertEquals("", WebhookAddress.read(file));
    }

    @Test
    void surroundingWhitespaceFromEditorsIsIgnored() throws Exception {
        var file = Files.writeString(folder.resolve(WebhookAddress.FILE_NAME), "  https://example.test/hook\r\n");
        assertEquals("https://example.test/hook", WebhookAddress.read(file));
    }
}
