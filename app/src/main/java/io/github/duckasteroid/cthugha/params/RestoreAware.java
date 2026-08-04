package io.github.duckasteroid.cthugha.params;

/**
 * Opt-in hook for a node whose leaf-value change listeners trigger an async or otherwise
 * order-sensitive side effect (e.g. {@code GeneratorRegistry} randomising and regenerating a
 * translation map when its "Generator" selector changes) that must not fire while {@link
 * ScreenConfigParams#apply} is replaying a whole-tree snapshot value-by-value — the side effect
 * would race the snapshot's own values for the same subtree and can clobber them.
 *
 * <p>{@link ScreenConfigParams#apply} calls {@link #beginRestore()} on every opted-in node
 * reachable from the root before applying any leaf value, and {@link #endRestore()} once every
 * value has been applied. {@link #endRestore()} is expected to leave the node in a state
 * consistent with the just-restored values itself (e.g. a deterministic, non-randomised
 * recompute), since the caller has no other way to trigger one for this subtree.</p>
 */
public interface RestoreAware {

    /** Suppresses this node's normal change-triggered side effects until {@link #endRestore()}. */
    void beginRestore();

    /**
     * Re-enables this node's normal change-triggered side effects, and brings it in line with
     * whatever values were just restored (without randomising or otherwise ignoring them).
     */
    void endRestore();
}
