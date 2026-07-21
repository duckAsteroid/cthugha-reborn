package io.github.duckasteroid.cthugha.screenconfig;

import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.DynamicChildList;
import io.github.duckasteroid.cthugha.params.DynamicChildList.ChildSpec;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.StringValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static utilities for capturing and applying a whole-tree "screen config" snapshot: a flat,
 * order-preserving map of path → value for every leaf {@link AbstractValue} (numeric) or
 * {@link StringValue} (string) node reachable from a root, excluding subtrees marked
 * {@link Node#isPersistExcluded()} — plus, for every opted-in {@link DynamicChildList} subtree
 * (e.g. {@code BindingSystem}), a recreation spec captured via {@link DynamicChildList#describe()}.
 *
 * <p>Param paths are slash-delimited node names relative to the captured root — the same
 * scheme {@code TabParams} uses for a single generator, generalised to the whole application
 * tree so a snapshot survives generator/palette selection changes and is independent of the
 * render resolution.</p>
 */
public class ScreenConfigParams {

    private ScreenConfigParams() {}

    /**
     * Combined result of {@link #capture}: the flat leaf-value map plus, keyed by subtree path,
     * the recreation spec for every opted-in {@link DynamicChildList} subtree encountered.
     */
    public record Snapshot(Map<String, Object> values, Map<String, List<ChildSpec>> dynamicChildren) {
        public Snapshot {
            values = values != null ? values : Map.of();
            dynamicChildren = dynamicChildren != null ? dynamicChildren : Map.of();
        }
    }

    /**
     * Walks {@code root}'s tree and returns a {@link Snapshot} combining an order-preserving flat
     * map of path → value for every leaf value node with a recreation spec for every opted-in
     * {@link DynamicChildList} subtree, skipping any subtree marked {@link Node#isPersistExcluded()}.
     *
     * <p>Insertion order matches tree traversal order, which matters for {@link #apply}: a
     * selector node (e.g. the active-generator enum) is always captured before the subtree it
     * selects, since it is registered as an earlier sibling.</p>
     */
    public static Snapshot capture(Node root) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        LinkedHashMap<String, List<ChildSpec>> dynamicChildren = new LinkedHashMap<>();
        walkCapture(root, "", values, dynamicChildren);
        return new Snapshot(values, dynamicChildren);
    }

    private static void walkCapture(Node node, String prefix, Map<String, Object> values,
                                     Map<String, List<ChildSpec>> dynamicChildren) {
        if (node.isPersistExcluded()) return;
        if (node instanceof AbstractValue av) {
            values.put(prefix, av.getValue());
        } else if (node instanceof StringValue sv) {
            values.put(prefix, sv.getValue());
        } else {
            if (node instanceof DynamicChildList dcl) {
                dynamicChildren.put(prefix, dcl.describe());
            }
            node.getChildren().forEach(child -> {
                String path = prefix.isEmpty() ? child.getName() : prefix + "/" + child.getName();
                walkCapture(child, path, values, dynamicChildren);
            });
        }
    }

    /**
     * Applies a saved {@link Snapshot} to {@code root}. First calls {@link
     * DynamicChildList#recreate(List)} on every opted-in subtree named in {@code
     * snapshot.dynamicChildren()} (so their runtime-created children exist again), then applies
     * {@code snapshot.values()} one entry at a time, in the order the entries were captured.
     *
     * <p>Each leaf-value entry re-resolves its path against the live tree independently (via
     * {@link Node#getChild(String[])}) rather than descending recursively once, because
     * applying an earlier entry (e.g. a generator selector) can restructure the tree — swapping
     * in a different child subtree — before later entries in the same snapshot are applied.
     * Unrecognised or type-mismatched paths are silently ignored.</p>
     */
    public static void apply(Node root, Snapshot snapshot) {
        applyDynamicChildren(root, snapshot.dynamicChildren());
        applyValues(root, snapshot.values());
    }

    private static void applyDynamicChildren(Node root, Map<String, List<ChildSpec>> dynamicChildren) {
        dynamicChildren.forEach((path, specs) -> {
            Node target = path.isEmpty() ? root : root.getChild(path.split("/")).orElse(null);
            if (target instanceof DynamicChildList dcl) {
                dcl.recreate(specs);
            }
        });
    }

    private static void applyValues(Node root, Map<String, Object> params) {
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
