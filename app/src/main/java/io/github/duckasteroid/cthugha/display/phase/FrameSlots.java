package io.github.duckasteroid.cthugha.display.phase;

import java.util.ArrayDeque;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hands frames from a producer thread to the GL thread through a fixed number of buffer slots,
 * without either side waiting. Each slot is free, being written (producer), pending (published,
 * not yet taken), or in flight (taken by the GL thread, whose GPU copy may still be reading it).
 *
 * <ul>
 *   <li>Producer: {@link #acquire()} a free slot (or drop the frame if there is none), fill it,
 *       then {@link #publish(int)} it. A pending slot the GL thread never took is recycled, so
 *       only the newest frame is ever shown.</li>
 *   <li>GL thread: {@link #takePending()} the newest frame, issue its upload, and once the
 *       upload has completed on the GPU, {@link #release(int)} the slot.</li>
 * </ul>
 */
final class FrameSlots {

    static final int NONE = -1;

    private final int count;
    private final ArrayBlockingQueue<Integer> free;
    private final AtomicInteger pending = new AtomicInteger(NONE);
    /** Slots taken by the GL thread and not yet released, oldest first. GL thread only. */
    private final ArrayDeque<Integer> inFlight = new ArrayDeque<>();

    FrameSlots(int count) {
        this.count = count;
        this.free = new ArrayBlockingQueue<>(count);
        for (int i = 0; i < count; i++) free.add(i);
    }

    int count() {
        return count;
    }

    /** Producer: a free slot to write into, or {@link #NONE} if all are in use. */
    int acquire() {
        Integer slot = free.poll();
        return slot == null ? NONE : slot;
    }

    /** Producer: gives back a slot it acquired but did not fill. */
    void discard(int slot) {
        free.add(slot);
    }

    /** Producer: makes {@code slot} the newest frame; a frame the GL thread hadn't taken is recycled. */
    void publish(int slot) {
        int replaced = pending.getAndSet(slot);
        if (replaced != NONE) free.add(replaced);
    }

    /** GL thread: the newest published frame, now in flight, or {@link #NONE}. */
    int takePending() {
        int slot = pending.getAndSet(NONE);
        if (slot != NONE) inFlight.add(slot);
        return slot;
    }

    /** GL thread: the oldest in-flight slot, or {@link #NONE}. */
    int oldestInFlight() {
        Integer slot = inFlight.peek();
        return slot == null ? NONE : slot;
    }

    /** GL thread: the oldest in-flight slot's upload has completed; it can be written again. */
    void release(int slot) {
        if (!inFlight.remove(slot)) throw new IllegalStateException("slot " + slot + " is not in flight");
        free.add(slot);
    }
}
