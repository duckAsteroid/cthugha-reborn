package io.github.duckasteroid.cthugha.remote;

import io.javalin.http.sse.SseClient;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.SubtreeChangeListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages SSE client registrations and dispatches parameter-change events.
 *
 * <p>Each connected client subscribes to one or more subtrees of the parameter tree via
 * {@link ParamNode#addSubtreeListener}.  When a descendant value changes, the subtree
 * listener runs on the render thread and enqueues a serialised JSON payload into a per-client
 * {@link ConcurrentHashMap} (last-write-wins deduplication — rapid changes to the same param
 * collapse to a single send per flush window).  A dedicated platform daemon thread flushes
 * each client's pending map at the configured interval, performing the actual {@code sendEvent}
 * I/O entirely off the render thread.</p>
 *
 * <p>{@link #broadcastAll} bypasses the queue for rare system events (token rotation, tree
 * structure changes) that must reach every client immediately.</p>
 */
public class RemoteEventBroadcaster {

    /** Lightweight snapshot of a parameter change captured on the render thread without serialization. */
    private record PendingEvent(double value, boolean controlled) {}

    /** Per-connected-client state. Not a record because {@code pending} must be atomically swappable. */
    static final class ClientRecord {
        final SseClient client;
        final List<SubtreeChangeListener> listeners;
        final List<ParamNode> subscribedNodes;
        volatile ConcurrentHashMap<String, PendingEvent> pending = new ConcurrentHashMap<>();

        ClientRecord(SseClient client, List<SubtreeChangeListener> listeners, List<ParamNode> subscribedNodes) {
            this.client = client;
            this.listeners = listeners;
            this.subscribedNodes = subscribedNodes;
        }
    }

    private final CopyOnWriteArrayList<ClientRecord> clients = new CopyOnWriteArrayList<>();
    private final ParamSerializer serializer = new ParamSerializer();
    private ScheduledExecutorService flusher;

    /**
     * Registers a new SSE client and attaches a {@link SubtreeChangeListener} on each of the
     * given nodes.  When any descendant value changes the listener enqueues the serialised event
     * into this client's pending map — no I/O on the render thread.
     */
    public void register(SseClient client, List<ParamNode> nodes) {
        List<SubtreeChangeListener> listeners = new ArrayList<>(nodes.size());
        ClientRecord record = new ClientRecord(client, listeners, new ArrayList<>(nodes));

        for (ParamNode node : nodes) {
            SubtreeChangeListener listener = (path, changedNode) -> {
                if (!(changedNode instanceof AbstractValue value)) return;
                try {
                    record.pending.put(path, new PendingEvent(value.getValue().doubleValue(), value.isControlled()));
                } catch (Exception ignored) {}
            };
            node.addSubtreeListener(listener);
            listeners.add(listener);
        }

        clients.add(record);
    }

    /** Unregisters the client and removes all subtree listeners it had installed. */
    public void unregister(SseClient client) {
        clients.removeIf(r -> {
            if (r.client != client) return false;
            for (int i = 0; i < r.subscribedNodes.size(); i++) {
                r.subscribedNodes.get(i).removeSubtreeListener(r.listeners.get(i));
            }
            return true;
        });
    }

    public boolean hasClients() {
        return !clients.isEmpty();
    }

    /** Starts a dedicated platform daemon thread that flushes pending events at {@code intervalMs}. */
    public void startFlushing(long intervalMs) {
        flusher = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("sse-flusher").factory());
        flusher.scheduleAtFixedRate(this::flush, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    /** Stops the flush scheduler. */
    public void stopFlushing() {
        if (flusher != null) {
            flusher.shutdownNow();
        }
    }

    /**
     * Drains each client's pending map and sends the accumulated events.
     * Runs exclusively on the sse-flusher thread.
     */
    private void flush() {
        for (ClientRecord record : clients) {
            ConcurrentHashMap<String, PendingEvent> batch = record.pending;
            if (batch.isEmpty()) continue;
            record.pending = new ConcurrentHashMap<>();
            batch.forEach((path, event) -> {
                try {
                    String payload = serializer.getMapper().writeValueAsString(
                            serializer.buildChangeEvent(path, event.value(), event.controlled()));
                    sendToClient(record.client, "paramChanged", payload);
                } catch (Exception ignored) {}
            });
        }
    }

    /** Sends {@code eventName} to every connected client immediately, bypassing the pending queue. */
    public void broadcastAll(String eventName, String jsonPayload) {
        for (ClientRecord record : clients) {
            sendToClient(record.client, eventName, jsonPayload);
        }
    }

    /**
     * Sends one event to {@code client}, skipping the call entirely if it's already terminated.
     *
     * <p>Without this guard, a client whose connection died mid-batch (e.g. a phone locking or
     * losing WiFi) logs one Javalin "SseClient has been terminated" warning per remaining pending
     * event in {@link #flush}'s batch for that client — {@code sendEvent} on an already-terminated
     * {@link SseClient} doesn't throw, it just logs and returns, so the previous try/catch here
     * never caught it. Checking {@link SseClient#terminated()} first turns a burst of duplicate
     * warnings (one per queued param change) into at most the one Javalin already logs internally
     * when it first discovers the dead connection.</p>
     */
    private void sendToClient(SseClient client, String eventName, String data) {
        if (client.terminated()) return;
        try {
            client.sendEvent(eventName, data);
        } catch (Exception e) {
            unregister(client);
        }
    }
}
