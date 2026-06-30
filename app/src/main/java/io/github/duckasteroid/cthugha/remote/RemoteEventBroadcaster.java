package io.github.duckasteroid.cthugha.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.sse.SseClient;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RemoteEventBroadcaster {

    public record ClientRecord(SseClient client, List<String> prefixes) {}

    private final CopyOnWriteArrayList<ClientRecord> clients = new CopyOnWriteArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public void register(SseClient client, List<String> prefixes) {
        clients.add(new ClientRecord(client, prefixes));
    }

    public void unregister(SseClient client) {
        clients.removeIf(r -> r.client() == client);
    }

    /** Sends to clients whose prefix list matches the event path, or to all if prefix list is empty. */
    public void broadcast(String eventName, String jsonPayload) {
        String path = extractPath(jsonPayload);
        for (ClientRecord record : clients) {
            if (record.prefixes().isEmpty() || matchesAnyPrefix(record.prefixes(), path)) {
                sendToClient(record.client(), eventName, jsonPayload);
            }
        }
    }

    /** Sends to all clients regardless of subscription (e.g. ping, tokenRotated). */
    public void broadcastAll(String eventName, String jsonPayload) {
        for (ClientRecord record : clients) {
            sendToClient(record.client(), eventName, jsonPayload);
        }
    }

    private void sendToClient(SseClient client, String eventName, String data) {
        try {
            client.sendEvent(eventName, data);
        } catch (Exception e) {
            unregister(client);
        }
    }

    private String extractPath(String jsonPayload) {
        try {
            JsonNode tree = mapper.readTree(jsonPayload);
            JsonNode pathNode = tree.get("path");
            return pathNode != null ? pathNode.asText() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private boolean matchesAnyPrefix(List<String> prefixes, String path) {
        for (String prefix : prefixes) {
            if (prefix.isEmpty() || path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }
}
