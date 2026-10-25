package io.github.duckasteroid.cthugha.display.phase;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static io.github.duckasteroid.cthugha.display.phase.FrameSlots.NONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameSlotsTest {

    @Test
    void handsTheNewestFrameToTheGlThread() {
        FrameSlots slots = new FrameSlots(3);
        int a = slots.acquire();
        slots.publish(a);
        int b = slots.acquire();
        slots.publish(b);
        // a was never taken, so it was recycled and b is the frame shown.
        assertEquals(b, slots.takePending());
        assertEquals(NONE, slots.takePending());
        assertNotEquals(NONE, slots.acquire());
        assertNotEquals(NONE, slots.acquire());
    }

    @Test
    void inFlightSlotsAreNotReusedUntilReleased() {
        FrameSlots slots = new FrameSlots(3);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            int s = slots.acquire();
            seen.add(s);
            slots.publish(s);
            assertEquals(s, slots.takePending());
        }
        assertEquals(Set.of(0, 1, 2), seen);
        // All three are in flight: the producer has to drop frames.
        assertEquals(NONE, slots.acquire());

        int oldest = slots.oldestInFlight();
        slots.release(oldest);
        assertEquals(oldest, slots.acquire());
    }

    @Test
    void discardReturnsAnUnfilledSlot() {
        FrameSlots slots = new FrameSlots(1);
        int s = slots.acquire();
        assertEquals(NONE, slots.acquire());
        slots.discard(s);
        assertEquals(s, slots.acquire());
    }

    @Test
    void releasingASlotThatIsNotInFlightFails() {
        FrameSlots slots = new FrameSlots(2);
        assertThrows(IllegalStateException.class, () -> slots.release(0));
    }

    @Test
    void releasesInFlightSlotsOldestFirst() {
        FrameSlots slots = new FrameSlots(3);
        int a = slots.acquire();
        slots.publish(a);
        slots.takePending();
        int b = slots.acquire();
        slots.publish(b);
        slots.takePending();
        assertEquals(a, slots.oldestInFlight());
        slots.release(a);
        assertEquals(b, slots.oldestInFlight());
        slots.release(b);
        assertEquals(NONE, slots.oldestInFlight());
        assertTrue(slots.acquire() != NONE);
    }
}
