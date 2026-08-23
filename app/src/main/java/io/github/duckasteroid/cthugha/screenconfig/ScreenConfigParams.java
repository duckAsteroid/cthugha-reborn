package io.github.duckasteroid.cthugha.screenconfig;

import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.DynamicChildList;
import io.github.duckasteroid.cthugha.params.DynamicChildList.ChildSpec;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.RestoreAware;
import io.github.duckasteroid.cthugha.params.StringValue;
import io.github.duckasteroid.cthugha.params.values.EnumParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Static utilities for capturing and applying a whole-tree "screen config" snapshot: a flat,
 * order-preserving map of path → value for every leaf {@link AbstractValue} (numeric),
 * {@link StringValue} (string), or {@link EnumParameter} (its selected option's display label,
 * not its integer index) node reachable from a root, excluding subtrees marked {@link
 * Node#isPersistExcluded()} — plus, for every opted-in {@link DynamicChildList} subtree (e.g.
 * {@code BindingSystem}), a recreation spec captured via {@link DynamicChildList#describe()}.
 *
 * <p>Param paths are slash-delimited node names relative to the captured root — the same
 * scheme {@code TabParams} uses for a single generator, generalised to the whole application
 * tree so a snapshot survives generator/palette selection changes and is independent of the
 * render resolution.</p>
 *
 * <p>{@link EnumParameter} options are keyed by label rather than index specifically because
 * several of them (the active palette, tab generator, quote, image, video, audio source) are
 * built from a runtime-scanned, sorted file/device list — inserting or removing one entry shifts
 * every index after it, so a saved index can silently select the wrong option after the library
 * changes. A label survives that; it only breaks if the option itself is renamed or removed, in
 * which case {@link #applyValues} leaves the target's current selection alone, same as any other
 * unresolved path. Configs captured before this existed still have plain numeric values for
 * those paths — {@link #applyValues} accepts either.</p>
 */
public class ScreenConfigParams {

    private static final Logger LOG = LoggerFactory.getLogger(ScreenConfigParams.class);

    private ScreenConfigParams() {}

    /**
     * Combined result of {@link #capture}: the flat leaf-value map, keyed by subtree path the
     * recreation spec for every opted-in {@link DynamicChildList} subtree encountered, and a
     * {@link #structureHash} of the tree shape at capture time.
     *
     * @param structureHash see {@link #structureHash(Node)}; {@code null} for a snapshot built
     *                       from a config saved before structural hashing existed, or one built
     *                       directly (e.g. in a test) via the two-argument constructor — either
     *                       way, {@link #apply} treats a {@code null} hash as "unknown" and skips
     *                       the structure-change check rather than reporting a false mismatch.
     */
    public record Snapshot(Map<String, Object> values, Map<String, List<ChildSpec>> dynamicChildren,
                            String structureHash) {
        public Snapshot {
            values = values != null ? values : Map.of();
            dynamicChildren = dynamicChildren != null ? dynamicChildren : Map.of();
        }

        /** Convenience constructor for callers with no structure hash (unknown/legacy). */
        public Snapshot(Map<String, Object> values, Map<String, List<ChildSpec>> dynamicChildren) {
            this(values, dynamicChildren, null);
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
        return new Snapshot(values, dynamicChildren, structureHash(root));
    }

    /**
     * Computes a SHA-256 digest of {@code root}'s tree <em>shape</em> — every non-excluded leaf's
     * path and kind (enum/number/string) — deliberately excluding both the leaf's current value
     * and, per {@link Node#isStructureHashExcluded()}, any subtree whose children are runtime or
     * user data rather than fixed by code (e.g. {@code Bindings}, {@code Wave}, saved-presets
     * listings). Tokens are collected into a {@link TreeSet} before hashing so the result depends
     * only on which paths exist, not on sibling declaration order.
     *
     * <p>Two trees with the same shape always hash identically regardless of current parameter
     * values; two trees that differ in which leaves exist (a param renamed, added, removed, or
     * changed type by a later code change) hash differently. Used by {@link #apply} to warn when
     * a config is being loaded against a tree shape different from the one it was saved against.</p>
     */
    public static String structureHash(Node root) {
        TreeSet<String> tokens = new TreeSet<>();
        walkStructure(root, "", tokens);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", tokens).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    private static void walkStructure(Node node, String prefix, Set<String> tokens) {
        if (node.isPersistExcluded()) return;
        if (node.isStructureHashExcluded()) {
            tokens.add(prefix + ":opaque");
            return;
        }
        if (node instanceof EnumParameter<?>) {
            tokens.add(prefix + ":enum");
        } else if (node instanceof AbstractValue av) {
            tokens.add(prefix + ":" + av.getNodeType());
        } else if (node instanceof StringValue) {
            tokens.add(prefix + ":string");
        } else {
            node.getChildren().forEach(child -> {
                String path = prefix.isEmpty() ? child.getName() : prefix + "/" + child.getName();
                walkStructure(child, path, tokens);
            });
        }
    }

    private static void walkCapture(Node node, String prefix, Map<String, Object> values,
                                     Map<String, List<ChildSpec>> dynamicChildren) {
        if (node.isPersistExcluded()) return;
        if (node instanceof EnumParameter<?> ep) {
            values.put(prefix, ep.getSelectedLabel());
        } else if (node instanceof AbstractValue av) {
            values.put(prefix, av.getValue());
        } else if (node instanceof StringValue sv) {
            values.put(prefix, sv.getValue());
        } else {
            if (node instanceof DynamicChildList dcl) {
                dcl.pruneOrphaned();
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
     * Unrecognised or type-mismatched paths are <em>not</em> silently ignored any more: each one
     * is logged at {@code WARN} as it's skipped (see {@link #applyValues}), and a one-line summary
     * is logged at {@code WARN} once the whole snapshot has been replayed if any were skipped, so
     * a config that fails to fully load is visible in the log without needing debug-level tracing.</p>
     *
     * <p>Every {@link RestoreAware} node reachable from {@code root} is put into "restore mode"
     * (via {@link RestoreAware#beginRestore()}) before values are applied and released (via
     * {@link RestoreAware#endRestore()}) once the whole snapshot has been replayed, so that
     * change-triggered side effects on those nodes (e.g. randomising and regenerating a
     * translation map on generator selection) don't race the snapshot's own values for the same
     * subtree.</p>
     *
     * <p>If {@code snapshot.structureHash()} is present and doesn't match {@code root}'s current
     * {@link #structureHash(Node)}, logs one {@code WARN} up front noting the tree shape has
     * changed since this config was saved — a heads-up before the per-path warnings below explain
     * specifically what didn't apply.</p>
     */
    public static void apply(Node root, Snapshot snapshot) {
        warnIfStructureChanged(root, snapshot.structureHash());
        applyDynamicChildren(root, snapshot.dynamicChildren());
        List<RestoreAware> restoreAware = collectRestoreAware(root);
        restoreAware.forEach(RestoreAware::beginRestore);
        try {
            int skipped = applyValues(root, snapshot.values());
            if (skipped > 0) {
                LOG.warn("Screen config apply: {} of {} value(s) could not be applied — see "
                        + "preceding warnings for the affected paths", skipped, snapshot.values().size());
            }
        } finally {
            restoreAware.forEach(RestoreAware::endRestore);
        }
    }

    private static void warnIfStructureChanged(Node root, String savedHash) {
        if (savedHash == null) return; // unknown/legacy — nothing to compare against
        String currentHash = structureHash(root);
        if (!savedHash.equals(currentHash)) {
            LOG.warn("Screen config apply: parameter tree structure differs from when this "
                    + "config was saved (structure hash {} at save, {} now) — some values may "
                    + "fail to apply; see following warnings for specifics", savedHash, currentHash);
        }
    }

    private static List<RestoreAware> collectRestoreAware(Node node) {
        List<RestoreAware> result = new ArrayList<>();
        walkRestoreAware(node, result);
        return result;
    }

    private static void walkRestoreAware(Node node, List<RestoreAware> result) {
        if (node instanceof RestoreAware ra) result.add(ra);
        node.getChildren().forEach(child -> walkRestoreAware(child, result));
    }

    private static void applyDynamicChildren(Node root, Map<String, List<ChildSpec>> dynamicChildren) {
        dynamicChildren.forEach((path, specs) -> {
            Node target = path.isEmpty() ? root : root.getChild(path.split("/")).orElse(null);
            if (target == null) {
                LOG.warn("Screen config: dynamic-child subtree '{}' no longer exists in the "
                        + "parameter tree — skipping recreation of {} saved child(ren)", path, specs.size());
            } else if (target instanceof DynamicChildList dcl) {
                dcl.recreate(specs);
            } else {
                LOG.warn("Screen config: '{}' is no longer a dynamic-child subtree (found {}) — "
                        + "skipping recreation of {} saved child(ren)",
                        path, target.getClass().getSimpleName(), specs.size());
            }
        });
    }

    /** Applies each captured leaf value against the live tree; returns how many were skipped. */
    private static int applyValues(Node root, Map<String, Object> params) {
        int skipped = 0;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String path = entry.getKey();
            Object value = entry.getValue();
            Node target = root.getChild(path.split("/")).orElse(null);
            if (target == null) {
                LOG.warn("Screen config: path '{}' no longer exists in the parameter tree — "
                        + "skipping (renamed or removed since this value was saved?)", path);
                skipped++;
            } else if (target instanceof EnumParameter<?> ep) {
                if (value instanceof String label) {
                    // Unresolved label (option renamed/removed since capture) is left unchanged,
                    // consistent with every other "unrecognised path" case in this method.
                    if (!ep.selectByLabel(label)) {
                        LOG.warn("Screen config: '{}' has no option labelled '{}' (renamed or "
                                + "removed since this config was saved?) — leaving current "
                                + "selection unchanged", path, label);
                        skipped++;
                    }
                } else if (value instanceof Number n) {
                    // Deprecated compatibility path: configs captured before enum options were
                    // keyed by label still carry a raw index here, which is only reliable if the
                    // option list hasn't been reordered since. Kept working, but flagged so it's
                    // visible which saved configs still need to be resaved to pick up the more
                    // robust label-based path.
                    LOG.warn("Screen config: '{}' uses a deprecated index-based selection ({}) "
                            + "instead of a label — resave this config to migrate it", path, n);
                    ep.setValue(n);
                } else {
                    LOG.warn("Screen config: '{}' expects a label (String) or legacy index "
                            + "(Number) but found {} — skipping", path, describe(value));
                    skipped++;
                }
            } else if (target instanceof AbstractValue av) {
                if (value instanceof Number n) {
                    av.setValue(n);
                } else {
                    LOG.warn("Screen config: '{}' expects a numeric value but found {} — skipping",
                            path, describe(value));
                    skipped++;
                }
            } else if (target instanceof StringValue sv) {
                if (value instanceof String s) {
                    sv.setValue(s);
                } else {
                    LOG.warn("Screen config: '{}' expects a string value but found {} — skipping",
                            path, describe(value));
                    skipped++;
                }
            } else {
                LOG.warn("Screen config: '{}' resolved to a {} which isn't a settable leaf — "
                        + "skipping", path, target.getClass().getSimpleName());
                skipped++;
            }
        }
        return skipped;
    }

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName() + " '" + value + "'";
    }
}
