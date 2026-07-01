package io.github.duckasteroid.cthugha.params;

import java.util.Random;

/**
 * Minimal context passed to {@link Action#execute} — provides access to the aspects of
 * the running application that actions need, without coupling action implementations
 * to concrete app classes.
 *
 * <p>Sub-interfaces (e.g. {@code TabActionContext}) extend this with domain-specific methods.</p>
 */
public interface ActionContext {
    /** Post a transient message to the on-screen notification overlay. */
    void notify(String message);

    /** Returns the application-wide random source. */
    Random rng();
}
