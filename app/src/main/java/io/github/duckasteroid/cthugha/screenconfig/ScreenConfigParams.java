package io.github.duckasteroid.cthugha.screenconfig;

import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.StringValue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static utilities for capturing and applying a whole-tree "screen config" snapshot: a flat,
 * order-preserving map of path → value for every leaf {@link AbstractValue} (numeric) or
 * {@link StringValue} (string) node reachable from a root, excluding subtrees marked
 * {@link Node#isPersistExcluded()}.
 *
 * <p>Param paths are slash-delimited node names relative to the captured root — the same
 * scheme {@code TabParams} uses for a single generator, generalised to the whole application
 * tree so a snapshot survives generator/palette selection changes and is independent of the
 * render resolution.</p>
 */
public class ScreenConfigParams {

    private ScreenConfigParams() {}

    /**
     * Walks {@code root}'s tree and returns an order-preserving flat map of path → value for
     * every leaf value node, skipping any subtree marked {@link Node#isPersistExcluded()}.
     *
     * <p>Insertion order matches tree traversal order, which matters for {@link #apply}: a
     * selector node (e.g. the active-generator enum) is always captured before the subtree it
     * selects, since it is registered as an earlier sibling.</p>
     */
    public static Map<String, Object> capture(Node root) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        walkCapture(root, "", result);
        return result;
    }

    private static void walkCapture(Node node, String prefix, Map<String, Object> out) {
        if (node.isPersistExcluded()) return;
        if (node instanceof AbstractValue av) {
            out.put(prefix, av.getValue());
        } else if (node instanceof StringValue sv) {
            out.put(prefix, sv.getValue());
        } else {
            node.getChildren().forEach(child -> {
                String path = prefix.isEmpty() ? child.getName() : prefix + "/" + child.getName();
                walkCapture(child, path, out);
            });
        }
    }

    /**
     * Applies a saved snapshot to {@code root}, one entry at a time, in the order the entries
     * were captured.
     *
     * <p>Each entry re-resolves its path against the live tree independently (via
     * {@link Node#getChild(String[])}) rather than descending recursively once, because
     * applying an earlier entry (e.g. a generator selector) can restructure the tree — swapping
     * in a different child subtree — before later entries in the same snapshot are applied.
     * Unrecognised or type-mismatched paths are silently ignored.</p>
     */
    public static void apply(Node root, Map<String, Object> params) {
        params.forEach((path, value) -> {
            Node target = root.getChild(path.split("/")).orElse(null);
            if (target instanceof AbstractValue av && value instanceof Number n) {
                av.setValue(n);
            } else if (target instanceof StringValue sv && value instanceof String s) {
                sv.setValue(s);
            }
        });
    }
}
