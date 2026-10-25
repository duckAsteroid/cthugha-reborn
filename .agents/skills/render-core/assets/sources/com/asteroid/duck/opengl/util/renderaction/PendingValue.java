package com.asteroid.duck.opengl.util.renderaction;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * An allocation-free, last-write-wins hand-off of a mutable value (e.g. a JOML matrix or vector)
 * from any thread to the GL thread.
 *
 * <p>Unlike enqueuing a lambda on a {@link RenderActionQueue}, {@link #offer} copies the value
 * into a preallocated slot instead of allocating a defensive copy and a capturing lambda per
 * call, and does nothing at all when the value is unchanged. That makes it cheap to call every
 * frame with live parameters.</p>
 *
 * <pre>{@code
 * private final PendingValue<Matrix4f> transform =
 *         new PendingValue<>(new Matrix4f(), (src, dst) -> dst.set(src));
 * private final Matrix4f glTransform = new Matrix4f(); // GL thread only
 *
 * public void setTransform(Matrix4f m) { transform.offer(m); }     // any thread
 *
 * // in doRender, on the GL thread:
 * if (transform.takeIfChanged(glTransform)) uTransform.set(glTransform);
 * }</pre>
 *
 * <p>Thread-safe: {@link #offer} and {@link #takeIfChanged} are synchronised on this instance,
 * and only ever copy values while holding the lock, so neither side sees a half-written value.</p>
 *
 * @param <T> the mutable value type; its {@code equals} must compare by value
 */
public final class PendingValue<T> {
    private final T pending;
    private final BiConsumer<? super T, ? super T> copier;
    private boolean changed;

    /**
     * @param initial the value the consumer starts with; owned by this instance from now on
     * @param copier  copies its first argument into its second, e.g. {@code (src, dst) -> dst.set(src)}
     */
    public PendingValue(T initial, BiConsumer<? super T, ? super T> copier) {
        this.pending = Objects.requireNonNull(initial);
        this.copier = Objects.requireNonNull(copier);
    }

    /**
     * Offer a new value. It is copied, so the caller may reuse {@code value} immediately.
     * A value equal to the latest offered one is ignored.
     *
     * @param value the new value; must not be {@code null}
     */
    public synchronized void offer(T value) {
        Objects.requireNonNull(value);
        if (pending.equals(value)) {
            return;
        }
        copier.accept(value, pending);
        changed = true;
    }

    /**
     * If a new value was offered since the last call, copy it into {@code dest}.
     *
     * @param dest consumer-owned destination for the latest value
     * @return {@code true} if {@code dest} was updated
     */
    public synchronized boolean takeIfChanged(T dest) {
        if (!changed) {
            return false;
        }
        copier.accept(pending, dest);
        changed = false;
        return true;
    }
}
