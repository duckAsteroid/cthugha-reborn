/**
 * The action sub-system of the Cthugha parameter tree.
 *
 * <h2>Overview</h2>
 * <p>Actions are the second kind of leaf node alongside
 * {@link io.github.duckasteroid.cthugha.params.AbstractValue}.  Where a value node holds a
 * readable/writable scalar, an action node exposes a single {@link io.github.duckasteroid.cthugha.params.action.Action#execute}
 * method that performs a side-effectful operation and returns nothing.</p>
 *
 * <h2>Types</h2>
 * <ul>
 *   <li>{@link io.github.duckasteroid.cthugha.params.action.Action} – the leaf-node interface.
 *       Extends {@link io.github.duckasteroid.cthugha.params.Node}; its
 *       {@link io.github.duckasteroid.cthugha.params.NodeType} is {@code ACTION}.</li>
 *   <li>{@link io.github.duckasteroid.cthugha.params.action.AbstractAction} – the standard
 *       implementation: a named node backed by a {@code Consumer<ActionContext>} lambda.</li>
 *   <li>{@link io.github.duckasteroid.cthugha.params.action.ActionContext} – the minimal context
 *       passed to every action at execution time.  Provides {@code notify(String)} for on-screen
 *       messages and {@code rng()} for access to the shared random source.</li>
 * </ul>
 *
 * <h2>Domain-specific contexts</h2>
 * <p>Components that define actions for their own domain extend {@code ActionContext} with
 * additional methods rather than coupling actions to concrete app classes:</p>
 * <ul>
 *   <li>{@code TabActionContext} ({@code tab} package) – exposes {@code tabStore()},
 *       {@code registry()}, {@code currentBuffer()}, and {@code loadTabBuffer()}.</li>
 *   <li>{@code PaletteActionContext} ({@code map} package) – exposes {@code currentPalette()}
 *       and {@code loadPalette()}.</li>
 *   <li>{@code CthughaActionContext} ({@code display} package) – top-level context that
 *       combines all sub-contexts.</li>
 * </ul>
 * <p>Action bodies cast the received {@code ActionContext} to the specific sub-interface they need:</p>
 * <pre>{@code
 * new AbstractAction("Save", ctx -> {
 *     if (ctx instanceof TabActionContext tctx) {
 *         tctx.tabStore().save(tctx.currentBuffer(), tctx.resolution());
 *         ctx.notify("Saved");
 *     }
 * });
 * }</pre>
 *
 * <h2>Remote access</h2>
 * <p>Actions are serialised into the remote param-tree JSON as nodes of type {@code ACTION}.
 * The remote UI renders them as buttons.  Sensitive actions (e.g. Quit) should be marked
 * {@link io.github.duckasteroid.cthugha.params.ParamNode#withNoRemote()} so they are omitted
 * from the JSON payload and rejected with 403 by the server.</p>
 */
package io.github.duckasteroid.cthugha.params.action;
