package team.civitatum.starclock.discord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Адрес содержит секретный токен, поэтому живёт отдельным файлом, который можно не хранить в git вместе с конфигом. */
public final class WebhookAddress {
    public static final String FILE_NAME = "civitatum_starclock-webhook.txt";

    /** Читается при каждой отправке: адрес можно поменять без перезапуска. Пустая строка отключает отправку. */
    public static String read(Path file) throws IOException {
        return Files.exists(file) ? Files.readString(file).strip() : "";
    }

    private WebhookAddress() {}
}
