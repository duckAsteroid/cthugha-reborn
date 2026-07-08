package io.github.duckasteroid.cthugha.work;

import com.asteroid.duck.opengl.util.RenderContext;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Manages named background work items that run on virtual threads.
 *
 * <p>Each key is a singleton: if work with the same key is already in-flight,
 * {@link #submit} returns {@code false} and the new request is dropped.
 * Completion actions are enqueued by the work itself via {@link WorkContext#enqueueRenderAction}
 * and drained on the GL thread each frame by {@link #processAll}.</p>
 */
public class BackgroundWorkQueue {

    private static class ActiveItem {
        final String label;
        volatile String status;

        ActiveItem(String label) {
            this.label = label;
            this.status = label;
        }
    }

    private final ConcurrentHashMap<String, ActiveItem> active = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Consumer<RenderContext>> completions = new ConcurrentLinkedQueue<>();

    /**
     * Submits work to run on a virtual thread.
     *
     * @return {@code true} if the work was accepted and started;
     *         {@code false} if work with the same key is already in-flight.
     */
    public boolean submit(String key, String label, Consumer<WorkContext> work) {
        ActiveItem item = new ActiveItem(label);
        if (active.putIfAbsent(key, item) != null) {
            return false;
        }

        WorkContext ctx = new WorkContext() {
            @Override public void setStatus(String message) { item.status = message; }
            @Override public void enqueueRenderAction(Consumer<RenderContext> action) { completions.add(action); }
        };

        Thread.ofVirtual().name("work-" + key).start(() -> {
            try {
                work.accept(ctx);
            } finally {
                active.remove(key, item);
            }
        });

        return true;
    }

    /** Called each frame on the GL thread — runs all pending completion actions. */
    public void processAll(RenderContext ctx) {
        Consumer<RenderContext> action;
        while ((action = completions.poll()) != null) {
            action.accept(ctx);
        }
    }

    public boolean isAnyActive() {
        return !active.isEmpty();
    }

    /** Returns the current status label of the first active work item, if any. */
    public Optional<String> activeStatus() {
        return active.values().stream().findFirst().map(i -> i.status);
    }
}
