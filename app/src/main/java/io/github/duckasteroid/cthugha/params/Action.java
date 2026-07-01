package io.github.duckasteroid.cthugha.params;

/**
 * A leaf node in the parameter tree that represents an invocable operation.
 *
 * <p>Alongside {@link AbstractValue} (which holds a mutable scalar), {@code Action} is the
 * second kind of leaf node.  Instead of a value to read/write, it exposes an
 * {@link #execute(ActionContext)} method that performs a side-effectful operation using
 * whatever aspects of the running application the supplied {@link ActionContext} exposes.</p>
 *
 * <p>The concrete implementation is {@link AbstractAction}.</p>
 */
public interface Action extends Node {
    /**
     * Executes this action using the provided context.
     *
     * @param ctx the runtime context; sub-interfaces (e.g. {@code TabActionContext}) provide
     *            domain-specific capabilities — implementations may cast as needed
     */
    void execute(ActionContext ctx);
}
