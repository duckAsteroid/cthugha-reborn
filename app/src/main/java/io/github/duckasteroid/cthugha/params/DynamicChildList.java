package io.github.duckasteroid.cthugha.params;

import java.util.List;
import java.util.Map;

/**
 * Opt-in contract for a {@link ParamNode} whose children are created and destroyed at runtime
 * through its own add/remove API, rather than being fixed at construction — e.g. {@code
 * BindingSystem}'s list of {@code Binding}s (one {@code Binding} per script), or a future {@code
 * WaveSystem}'s list of named wave instances.
 *
 * <p>{@link ScreenConfigParams} only knows how to walk and set values on leaf nodes that already
 * exist in the live tree. That's fine for the static part of the tree, but a subtree implementing
 * this interface has children that don't exist at all in a fresh session (or a session where the
 * previous set of children differs in number/order from the one being restored) — so before
 * {@code ScreenConfigParams.apply} can set e.g. {@code Bindings/Trigger 1/condition}, something
 * has to first recreate a child actually named "Trigger 1" in that position. That's what this
 * interface is for: {@link #describe()} captures enough structural information to recreate each
 * child, and {@link #recreate(List)} rebuilds the child list from such a description, in order,
 * using the implementer's own existing {@code addX(...)} primitives — no behavioural change to
 * the owning system itself, just a new entry point config-loading can call.</p>
 *
 * <p>{@link ScreenConfigParams#capture} calls {@link #describe()} on every opted-in subtree it
 * encounters (alongside the flat leaf-value map it already produces) and {@link
 * ScreenConfigParams#apply} calls {@link #recreate(List)} on every opted-in subtree named in a
 * snapshot <em>before</em> it walks leaf values, so that by the time leaf-value application runs,
 * the recreated children's paths already exist to receive their values.</p>
 */
public interface DynamicChildList {

    /**
     * Returns a description of this node's current dynamic children, in the order they should be
     * recreated. Must contain enough information for {@link #recreate(List)} to rebuild an
     * equivalent child list via this system's own {@code addX(...)} primitives — including
     * whatever name each child was given, since later leaf-value application resolves children by
     * path/name.
     */
    List<ChildSpec> describe();

    /**
     * Clears this node's existing dynamic children and rebuilds them from {@code specs}, in
     * order, via this system's own {@code addX(...)} primitives. Called by {@link
     * ScreenConfigParams#apply} before any leaf value is applied.
     */
    void recreate(List<ChildSpec> specs);

    /**
     * Minimal, implementation-agnostic description of one dynamic child: the name it was (or
     * should be) given, a free-form type discriminator the implementer defines and interprets
     * (e.g. a {@code BindingMode} name today; a wave-kind name for a future {@code WaveSystem}),
     * and whatever primitive string fields the implementer's own {@code addX(...)} call needs
     * (e.g. {@code target}/{@code script} for a continuous binding). Deliberately flat and
     * string-typed so it round-trips through JSON with no bespoke (de)serialisation.
     */
    record ChildSpec(String name, String type, Map<String, String> fields) {
        public ChildSpec {
            fields = Map.copyOf(fields);
        }
    }
}
