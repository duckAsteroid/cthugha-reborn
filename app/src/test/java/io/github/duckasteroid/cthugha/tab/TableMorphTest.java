package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.tab.TableMorph.Duration;
import io.github.duckasteroid.cthugha.tab.TableMorph.Easing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TableMorphTest {

    private static final long MS = 1_000_000L;

    /** Tracks what each of the three textures holds, as the GL side would. */
    private static final class FakeSlots implements TableMorph.Slots {
        final Object[] tex = new Object[3];
        final int[] slot = {0, 1};
        int spare = 2;
        final List<String> ops = new ArrayList<>();

        @Override public void uploadSpare(TabBuffer table) { tex[spare] = table; ops.add("upload"); }
        @Override public void bakeIntoSpare(double mix) {
            tex[spare] = "bake(" + tex[slot[0]] + "," + tex[slot[1]] + "@" + mix + ")";
            ops.add("bake");
        }
        @Override public void promoteSpare(int s) {
            int old = slot[s]; slot[s] = spare; spare = old; ops.add("promote" + (s == 0 ? "A" : "B"));
        }
        Object a() { return tex[slot[0]]; }
        Object b() { return tex[slot[1]]; }
    }

    private TableMorph morph;
    private FakeSlots slots;
    private final TabBuffer t0 = table(), t1 = table(), t2 = table();

    private static TabBuffer table() { return new TabBuffer(new Dimension(2, 2)); }

    @BeforeEach
    void setUp() {
        morph = new TableMorph();
        slots = new FakeSlots();
        morph.setSlots(slots);
        morph.duration.setValue(Duration.ONE.ordinal());
        morph.easing.setValue(Easing.LINEAR.ordinal());
        morph.fillBoth(t0);
        slots.ops.clear();
    }

    @Test
    void fillBothPutsTheTableInBothSlots() {
        assertSame(t0, slots.a());
        assertSame(t0, slots.b());
    }

    @Test
    void newTableLoadsIntoTheLessVisibleSlotAndMorphsToItOverTheDuration() {
        morph.onNewTable(t1, 0);
        assertSame(t0, slots.a());
        assertSame(t1, slots.b());
        assertEquals(0.0, morph.mix.value, 1e-9);

        morph.frame(500 * MS);
        assertEquals(0.5, morph.mix.value, 1e-9);
        morph.frame(1000 * MS);
        assertEquals(1.0, morph.mix.value, 1e-9);

        // The next table goes into A, the now less visible slot, and mix heads back to 0.
        morph.onNewTable(t2, 2000 * MS);
        assertSame(t2, slots.a());
        assertSame(t1, slots.b());
        morph.frame(3000 * MS);
        assertEquals(0.0, morph.mix.value, 1e-9);
    }

    @Test
    void smoothEasingEasesInAndOut() {
        morph.easing.setValue(Easing.SMOOTH.ordinal());
        morph.onNewTable(t1, 0);
        morph.frame(250 * MS);
        assertEquals(0.15625, morph.mix.value, 1e-9); // smoothstep(0.25)
        morph.frame(500 * MS);
        assertEquals(0.5, morph.mix.value, 1e-9);
    }

    @Test
    void durationNoneSwapsInstantly() {
        morph.duration.setValue(Duration.NONE.ordinal());
        morph.onNewTable(t1, 0);
        assertSame(t1, slots.b());
        assertEquals(1.0, morph.mix.value, 1e-9);
        assertEquals(List.of("upload", "promoteB"), slots.ops);
    }

    @Test
    void aTableArrivingMidMorphBakesTheCurrentLookFirst() {
        morph.onNewTable(t1, 0);
        morph.frame(300 * MS); // mix 0.3: mostly A (t0), partly B (t1)
        slots.ops.clear();

        morph.onNewTable(t2, 300 * MS);

        assertEquals(List.of("bake", "promoteA", "upload", "promoteB"), slots.ops);
        assertEquals("bake(" + t0 + "," + t1 + "@0.3)", slots.a());
        assertSame(t2, slots.b());
        assertEquals(0.0, morph.mix.value, 1e-9, "mix restarts from the baked slot's end");
        morph.frame(1300 * MS);
        assertEquals(1.0, morph.mix.value, 1e-9);
    }

    @Test
    void aBindingOwnedMixIsNotMovedAndTheTableLandsWhenItsSlotIsNearlyHidden() {
        morph.mix.setControlled(true);
        morph.mix.setValue(0.4);
        morph.onNewTable(t1, 0);
        morph.frame(10 * MS);
        assertEquals(0.4, morph.mix.value, 1e-9);
        assertSame(t0, slots.b(), "B is 40% visible: not swapped yet");

        morph.mix.setValue(0.03); // the binding brings B down to 3% visible
        morph.frame(20 * MS);
        assertSame(t1, slots.b());
        assertEquals(0.03, morph.mix.value, 1e-9);
    }

    @Test
    void aBindingOwnedMixHeldMidWaySwapsAfterTheTimeout() {
        morph.mix.setControlled(true);
        morph.mix.setValue(0.5);
        morph.onNewTable(t1, 0);
        morph.frame(900 * MS);
        assertSame(t0, slots.a());
        assertSame(t0, slots.b());
        morph.frame(1000 * MS); // the Morph duration (1 s) has passed
        assertSame(t1, slots.a(), "at exactly 0.5, A counts as the less visible slot");
    }

    @Test
    void releasingTheBindingWithATablePendingMorphsToIt() {
        morph.mix.setControlled(true);
        morph.mix.setValue(0.4);
        morph.onNewTable(t1, 0);
        morph.mix.setControlled(false);
        slots.ops.clear();

        morph.frame(100 * MS);

        assertEquals(List.of("bake", "promoteA", "upload", "promoteB"), slots.ops);
        assertSame(t1, slots.b());
    }
}
