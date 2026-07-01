package io.github.duckasteroid.cthugha.params;

import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Base class for leaf nodes that hold a mutable {@link String} value.
 *
 * <p>Parallel to {@link AbstractValue} (which holds a bounded {@link Number}), but strings
 * have no meaningful min/max or normalisation so they do not extend it.  Change listeners
 * are provided for consistency with the rest of the param system.</p>
 *
 * <p>The concrete implementation is {@link io.github.duckasteroid.cthugha.params.values.StringParameter}.</p>
 */
public abstract class StringValue extends AbstractNode {

    private final CopyOnWriteArrayList<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    public StringValue(String name) {
        super(name);
    }

    public abstract String getValue();

    /** Sets the value; implementations must call {@link #fireChangeListeners()} after updating. */
    public abstract void setValue(String value);

    @Override
    public NodeType getNodeType() { return NodeType.STRING; }

    @Override
    public boolean isLeaf() { return true; }

    @Override
    public void randomise(Random rng) { /* no-op — strings have no random range */ }

    public void addChangeListener(Runnable r)    { changeListeners.add(r); }
    public void removeChangeListener(Runnable r) { changeListeners.remove(r); }

    protected void fireChangeListeners() { changeListeners.forEach(Runnable::run); }
}
