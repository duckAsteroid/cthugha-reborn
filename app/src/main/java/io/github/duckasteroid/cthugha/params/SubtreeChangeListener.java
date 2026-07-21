package io.github.duckasteroid.cthugha.params;

/**
 * Listener that is notified when any value-typed descendant of a subscribed {@link ParamNode}
 * changes.  Register via {@link ParamNode#addSubtreeListener} on any interior (container) or
 * leaf node; the notification bubbles up from the changed leaf to every registered ancestor.
 */
@FunctionalInterface
public interface SubtreeChangeListener {
    /**
     * @param path        slash-delimited path of the changed node from the tree root
     * @param changedNode the leaf node whose value changed
     */
    void changed(String path, ParamNode changedNode);
}
