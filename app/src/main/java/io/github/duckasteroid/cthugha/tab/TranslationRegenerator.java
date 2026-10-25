package io.github.duckasteroid.cthugha.tab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Coalesces translation-table regeneration requests so at most one runs at a time and the latest
 * request always lands.
 *
 * <p>A request made while a run is in flight is not dropped: it is held as the single pending
 * request, and when the run finishes it runs once more. Any number of requests made during one
 * run (e.g. a slider drag on a generator parameter) therefore cost exactly one follow-up run,
 * which sees the then-current parameter values.
 *
 * <p>Requests merge by strength: a pending {@link Request#RANDOMISE} is not downgraded by a later
 * {@link Request#REGENERATE}, since the randomise would overwrite the parameters that regenerate
 * would read anyway.
 */
public final class TranslationRegenerator {

    private static final Logger LOG = LoggerFactory.getLogger(TranslationRegenerator.class);

    /** What a run does, weakest first. */
    public enum Request {
        /** Regenerate from the current generator's current parameter values. */
        REGENERATE,
        /** Randomise the current generator's parameters, then regenerate. */
        RANDOMISE
    }

    private final Consumer<Runnable> starter;
    private final Consumer<Request> job;

    private Request pending;
    private boolean running;

    /**
     * @param starter runs the given drain loop on a background thread
     * @param job     performs one run for the given request, blocking until its result is in use
     */
    public TranslationRegenerator(Consumer<Runnable> starter, Consumer<Request> job) {
        this.starter = starter;
        this.job = job;
    }

    /** Requests a run; starts one if none is in flight, otherwise merges into the pending run. */
    public void request(Request request) {
        synchronized (this) {
            if (pending == null || request.ordinal() > pending.ordinal()) {
                pending = request;
            }
            if (running) return;
            running = true;
        }
        starter.accept(this::drain);
    }

    private void drain() {
        while (true) {
            Request next;
            synchronized (this) {
                next = pending;
                pending = null;
                if (next == null) {
                    running = false;
                    return;
                }
            }
            try {
                job.accept(next);
            } catch (RuntimeException e) {
                LOG.error("Translation regeneration failed", e);
            } catch (Throwable t) {
                synchronized (this) {
                    running = false;
                }
                throw t;
            }
        }
    }
}
