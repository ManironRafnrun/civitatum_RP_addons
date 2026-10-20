package team.civitatum.starclock.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import team.civitatum.starclock.StarclockClient;

public final class StarclockNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        // Обработчики обращаются к клиентским классам только при получении S2C-пакета.
        registrar.playToClient(RitualStatePayload.TYPE, RitualStatePayload.CODEC,
                (packet, context) -> context.enqueueWork(() -> StarclockClient.state(packet)));
        registrar.playToClient(ShowCardPayload.TYPE, ShowCardPayload.CODEC,
                (packet, context) -> context.enqueueWork(() -> StarclockClient.card(packet)));
        registrar.playToClient(CancelRitualPayload.TYPE, CancelRitualPayload.CODEC,
                (packet, context) -> context.enqueueWork(() -> StarclockClient.cancel(packet)));
    }

    private StarclockNetwork() {}
}
