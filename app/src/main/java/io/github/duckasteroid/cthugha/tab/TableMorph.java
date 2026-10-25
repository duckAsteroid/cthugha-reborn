package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.EnumParameter;

import java.util.Arrays;

/**
 * Morphs the translation table between two resident slots, A and B, instead of swapping it
 * instantly — the "Morph" group on the Tab tab.
 *
 * <p>The translate pass blends the two slots' displacements by {@link #mix} (0 = all A, 1 = all
 * B). A new table always loads into the less visible slot; then, unless {@link #duration} is
 * {@link Duration#NONE}, {@link #mix} is moved to that slot over the duration with the chosen
 * {@link #easing}. A new table arriving while {@link #mix} is between 0 and 1 first has the
 * current mixed look baked into the nearer slot (see {@link Slots#bakeIntoSpare}), so nothing on
 * screen jumps.
 *
 * <p>{@link #mix} is an ordinary animatable parameter. While a continuous binding drives it, the
 * binding owns it: a new table doesn't move {@link #mix}, but waits until its slot is at most
 * {@link #SWAP_VISIBILITY} visible (or {@link #ownedSwapTimeoutSeconds} has passed) and then
 * replaces the less visible slot, so a breathing morph carries on into the new table.
 *
 * <p>All methods run on the GL thread. The GL work is behind {@link Slots}: three map textures
 * whose roles rotate — slot A, slot B, and a spare that new tables are uploaded or baked into
 * before being promoted into a slot.
 */
public class TableMorph extends ParamNode {

    /** A slot at or below this visibility can be replaced without a visible jump. */
    static final double SWAP_VISIBILITY = 0.05;
    /** Swap timeout for a binding-owned Mix when {@link #duration} is {@link Duration#NONE}. */
    static final double DEFAULT_OWNED_SWAP_TIMEOUT_SECONDS = 1.0;

    /** GL-side operations on the slot textures. */
    public interface Slots {
        /** Uploads {@code table} into the spare texture. */
        void uploadSpare(TabBuffer table);

        /** Writes the current blend of A and B at {@code mix} into the spare texture. */
        void bakeIntoSpare(double mix);

        /** Makes the spare texture slot {@code slot} (0 = A, 1 = B); the slot's old texture becomes the spare. */
        void promoteSpare(int slot);
    }

    /** How long a map change takes to morph in. */
    public enum Duration {
        NONE("None", 0), QUARTER("0.25 s", 0.25), HALF("0.5 s", 0.5), ONE("1 s", 1), TWO("2 s", 2),
        FOUR("4 s", 4), EIGHT("8 s", 8);

        private final String label;
        final double seconds;

        Duration(String label, double seconds) {
            this.label = label;
            this.seconds = seconds;
        }

        @Override public String toString() { return label; }
    }

    /** The morph's progress curve. */
    public enum Easing {
        LINEAR("Linear"), SMOOTH("Smooth");

        private final String label;

        Easing(String label) { this.label = label; }

        double apply(double p) {
            return this == SMOOTH ? p * p * (3 - 2 * p) : p;
        }

        @Override public String toString() { return label; }
    }

    public final EnumParameter<Duration> duration =
            new EnumParameter<>("Duration", Arrays.asList(Duration.values()), Duration.ONE);
    public final EnumParameter<Easing> easing =
            new EnumParameter<>("Easing", Arrays.asList(Easing.values()), Easing.SMOOTH);
    public final DoubleParameter mix = new DoubleParameter("Mix", 0.0, 1.0, 0.0);

    private Slots slots;

    // Automatic morph in flight (only while mix isn't binding-owned)
    private boolean morphing;
    private double morphFrom;
    private double morphTarget;
    private long morphStartNanos;
    private long morphDurationNanos;

    // A table waiting for a binding-owned mix to reach a swap point. It is already uploaded into
    // the spare texture; the reference is kept so it can be re-uploaded if the binding lets go
    // mid-mix and the spare is needed for a bake first. The regenerator only reuses a buffer after
    // a newer table is adopted, which replaces this one, so holding it is safe.
    private TabBuffer pending;
    private long pendingSinceNanos;

    public TableMorph() {
        super("Morph");
        withUiHint(UiHint.ICON, "blend");
        withDescription("How a new translation table takes over from the current one: it morphs "
            + "in, bending the flow from one shape to the other, instead of switching instantly.");
        duration.withDescription("How long a new table (preset, generator, randomise or a "
            + "parameter change) takes to morph in. None switches instantly.");
        duration.withNoAnimate();
        easing.withDescription("Smooth eases the morph in and out; Linear moves at a constant rate.");
        easing.withNoAnimate();
        mix.withDescription("Blend between the two most recent tables (0 = one, 1 = the other). "
            + "Moves by itself when a new table morphs in; animate it to breathe between two "
            + "tables, e.g. load two presets one after the other, then bind sine(0.05).");
        addChild(duration);
        addChild(easing);
        addChild(mix);
    }

    /** Binds the GL-side slot operations; call once the slot textures exist. */
    public void setSlots(Slots slots) {
        this.slots = slots;
    }

    /**
     * Fills both slots with {@code table} instantly, cancelling any morph or pending swap — for
     * the first table and for a restored screen config.
     */
    public void fillBoth(TabBuffer table) {
        morphing = false;
        pending = null;
        slots.uploadSpare(table);
        slots.promoteSpare(0);
        slots.uploadSpare(table);
        slots.promoteSpare(1);
    }

    /** Takes over a newly active table: morphs to it, or queues it for a binding-owned mix. */
    public void onNewTable(TabBuffer table, long nowNanos) {
        if (mix.isControlled()) {
            morphing = false;
            slots.uploadSpare(table);
            if (pending == null) pendingSinceNanos = nowNanos;
            pending = table;
            return;
        }
        pending = null;
        morphTo(table, nowNanos);
    }

    /** Advances an automatic morph, or lands a pending table under a binding-owned mix. Once per frame. */
    public void frame(long nowNanos) {
        if (mix.isControlled()) {
            morphing = false;
            if (pending != null) {
                double m = mix.value;
                int lessVisible = m < 0.5 ? 1 : 0;
                double visibility = lessVisible == 1 ? m : 1 - m;
                if (visibility <= SWAP_VISIBILITY
                        || nowNanos - pendingSinceNanos >= seconds(ownedSwapTimeoutSeconds())) {
                    slots.promoteSpare(lessVisible);
                    pending = null;
                }
            }
            return;
        }
        if (pending != null) {
            // The binding let go of mix before the table landed: morph to it as normal.
            TabBuffer table = pending;
            pending = null;
            morphTo(table, nowNanos);
        }
        if (morphing) {
            double p = morphDurationNanos <= 0 ? 1.0
                    : Math.min(1.0, (double) (nowNanos - morphStartNanos) / morphDurationNanos);
            mix.setValue(morphFrom + (morphTarget - morphFrom) * easing.getEnumeration().apply(p));
            if (p >= 1.0) morphing = false;
        }
    }

    /** Loads {@code table} into the less visible slot and moves mix to it (instantly with no duration). */
    private void morphTo(TabBuffer table, long nowNanos) {
        Duration d = duration.getEnumeration();
        double m = mix.value;
        if (m > 0.0 && m < 1.0 && d != Duration.NONE) {
            // Mid-mix: bake the current look into the nearer slot, so the morph starts from it.
            int nearer = m < 0.5 ? 0 : 1;
            slots.bakeIntoSpare(m);
            slots.promoteSpare(nearer);
            m = nearer;
            mix.setValue(m);
        }
        int far = m < 0.5 ? 1 : 0;
        slots.uploadSpare(table);
        slots.promoteSpare(far);
        if (d == Duration.NONE) {
            morphing = false;
            mix.setValue(far);
            return;
        }
        morphing = true;
        morphFrom = m;
        morphTarget = far;
        morphStartNanos = nowNanos;
        morphDurationNanos = seconds(d.seconds);
    }

    double ownedSwapTimeoutSeconds() {
        double s = duration.getEnumeration().seconds;
        return s > 0 ? s : DEFAULT_OWNED_SWAP_TIMEOUT_SECONDS;
    }

    private static long seconds(double s) {
        return (long) (s * 1e9);
    }
}
