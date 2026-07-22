package io.github.duckasteroid.cthugha.screenconfig;

import io.github.duckasteroid.cthugha.params.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Periodically flushes the live parameter tree to the "current" state file (see
 * {@link CurrentStateStore}), so a crash loses at most one interval's worth of session state
 * rather than the whole session (see issue #3).
 *
 * <h2>Write cadence</h2>
 * <p>A true debounce-after-last-change (reset a timer on every change, fire once it goes quiet)
 * does not work here: {@code ContinuousBinding} animations mutate their target's value on every
 * single CPU tick, so a debounce timer would keep getting reset and might never fire while any
 * animation is running — exactly the scenario this feature most needs to survive a crash during.
 * Instead this uses a fixed-interval timer (default 5s, tunable via {@code [session]
 * current_state_interval} in {@code cthugha.ini}) that re-captures and re-serialises the tree
 * every tick, but only actually writes to disk when the serialised snapshot differs from the
 * last write — cheap enough to run unconditionally, and avoids disk churn when the session is
 * idle (paused, no bindings, no remote edits).</p>
 *
 * <p>The scheduling and dirty-check logic is deliberately separated from GL/window lifecycle so
 * it can be unit tested by calling {@link #tick()} directly instead of waiting on a real
 * scheduler.</p>
 */
public class CurrentStatePersister {

    private static final Logger LOG = LoggerFactory.getLogger(CurrentStatePersister.class);

    /** Default write interval if not overridden — see class docs. */
    public static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(5);

    private final CurrentStateStore store;
    private final Node treeRoot;
    private final Duration interval;

    private final AtomicReference<String> lastWritten = new AtomicReference<>(null);
    private volatile ScheduledExecutorService scheduler;

    public CurrentStatePersister(CurrentStateStore store, Node treeRoot) {
        this(store, treeRoot, DEFAULT_INTERVAL);
    }

    public CurrentStatePersister(CurrentStateStore store, Node treeRoot, Duration interval) {
        this.store = store;
        this.treeRoot = treeRoot;
        this.interval = interval;
    }

    /** Starts the periodic background flush. Safe to call at most once; a second call is a no-op. */
    public synchronized void start() {
        if (scheduler != null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "current-state-writer");
            t.setDaemon(true);
            return t;
        });
        long ms = Math.max(1, interval.toMillis());
        scheduler.scheduleWithFixedDelay(this::tickSafely, ms, ms, TimeUnit.MILLISECONDS);
    }

    /** Stops the periodic flush (if running) and performs one final synchronous flush. */
    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        flushNow();
    }

    /**
     * Immediately (re-)captures and writes the current tree state, bypassing the dirty check —
     * used for a best-effort final flush (clean shutdown, or a JVM shutdown-hook backstop for
     * abnormal termination). Safe to call from any thread, any number of times, and before
     * {@link #start()} has ever been called; swallows and logs errors rather than throwing, since
     * callers include shutdown paths where a failure here must not block the rest of shutdown.
     */
    public void flushNow() {
        try {
            String json = store.serialize(treeRoot);
            store.writeSerialized(json);
            lastWritten.set(json);
        } catch (Exception e) {
            LOG.warn("Failed to flush current state", e);
        }
    }

    /**
     * One scheduling cycle: capture + serialise the tree, and write only if it differs from the
     * last write. Package-visible (rather than private) so tests can drive it directly instead of
     * depending on wall-clock scheduling.
     */
    void tick() throws Exception {
        String json = store.serialize(treeRoot);
        if (!json.equals(lastWritten.get())) {
            store.writeSerialized(json);
            lastWritten.set(json);
        }
    }

    private void tickSafely() {
        try {
            tick();
        } catch (Exception e) {
            LOG.warn("Failed to write current state", e);
        }
    }
}
