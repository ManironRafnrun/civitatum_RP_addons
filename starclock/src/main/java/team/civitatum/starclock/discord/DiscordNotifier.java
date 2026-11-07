package team.civitatum.starclock.discord;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.neoforged.fml.loading.FMLPaths;
import team.civitatum.starclock.CivitatumStarclock;
import team.civitatum.starclock.network.ShowCardPayload;

/** Отправка не должна задерживать тик сервера, а её сбой не влияет на игру: сообщение просто теряется. */
public final class DiscordNotifier {
    private interface Delivery {
        int send(String url) throws IOException;
    }

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "Starclock Discord");
        thread.setDaemon(true);
        return thread;
    });

    public static void predictionRead(String player, ShowCardPayload card) {
        var text = new PredictionMessage.Card(card.sign(), card.orientation(), card.tier().name(),
                card.category(), card.target());
        deliver(url -> WebhookClient.post(url, PredictionMessage.read(player, text),
                CardImage.render(text.sign(), text.orientation()), PredictionMessage.IMAGE_NAME));
    }

    public static void predictionsCancelled(int count) {
        deliver(url -> WebhookClient.post(url, PredictionMessage.cancelled(count)));
    }

    private static void deliver(Delivery delivery) {
        WORKER.execute(() -> {
            try {
                String url = WebhookAddress.read(FMLPaths.CONFIGDIR.get().resolve(WebhookAddress.FILE_NAME));
                if (url.isEmpty()) return;
                int status = delivery.send(url);
                if (status / 100 != 2) CivitatumStarclock.LOG.error("Discord отклонил сообщение: HTTP {}", status);
            } catch (Exception error) {
                // В адресе вебхука лежит секретный токен, а текст сетевых ошибок может его содержать.
                CivitatumStarclock.LOG.error("Не удалось отправить в Discord: {}", error.getClass().getName());
            }
        });
    }

    private DiscordNotifier() {}
}
