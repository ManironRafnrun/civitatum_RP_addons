package team.civitatum.coupleinteractions.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-thread-only request storage. A decision never supplies its sender or target. */
public final class RequestBook {
    public record Request(UUID id, UUID sender, UUID recipient, Interaction action, long expiresAt) {}
    private final Map<UUID, Request> byId = new HashMap<>();
    private final Map<UUID, UUID> byPlayer = new HashMap<>();
    private final Map<UUID, Long> nextRequest = new HashMap<>();

    public boolean busy(UUID player) { return byPlayer.containsKey(player); }

    public boolean rateLimit(UUID sender, long now, int cooldown) {
        if (nextRequest.getOrDefault(sender, 0L) > now) return false;
        nextRequest.put(sender, now + cooldown);
        return true;
    }

    public Request create(UUID sender, UUID recipient, Interaction action, long expiresAt) {
        if (sender.equals(recipient) || busy(sender) || busy(recipient) || action == Interaction.RELEASE) return null;
        Request request = new Request(UUID.randomUUID(), sender, recipient, action, expiresAt);
        byId.put(request.id(), request);
        byPlayer.put(sender, request.id());
        byPlayer.put(recipient, request.id());
        return request;
    }

    public Request decide(UUID requestId, UUID recipient, long now) {
        Request request = byId.get(requestId);
        // A forged response must not consume another player's invitation.
        if (request == null || !request.recipient().equals(recipient)) return null;
        remove(request);
        return now < request.expiresAt() ? request : null;
    }

    public Request cancel(UUID player) {
        UUID id = byPlayer.get(player);
        Request request = id == null ? null : byId.get(id);
        if (request != null) remove(request);
        return request;
    }

    public List<Request> expire(long now) {
        List<Request> expired = new ArrayList<>();
        for (Request request : List.copyOf(byId.values())) {
            if (now >= request.expiresAt()) { remove(request); expired.add(request); }
        }
        nextRequest.entrySet().removeIf(e -> e.getValue() <= now);
        return expired;
    }

    public void forget(UUID player) { nextRequest.remove(player); }
    public int size() { return byId.size(); }

    private void remove(Request request) {
        byId.remove(request.id());
        byPlayer.remove(request.sender(), request.id());
        byPlayer.remove(request.recipient(), request.id());
    }
}
