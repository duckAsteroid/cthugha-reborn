package com.asteroid.duck.opengl.util.keys;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A registered keyboard action: pairs a {@link KeyCombination} with a {@link Runnable} and a
 * human-readable description.
 *
 * <p>Registered through {@link KeyRegistry}; the registry holds the canonical reference and
 * dispatches GLFW key events to the correct action. Actions can be temporarily disabled via
 * {@link #setEnabled(boolean)} without removing them from the registry, making it easy to
 * suppress interactions during modal states.</p>
 */
public class KeyAction implements Comparable<KeyAction> {
	private static final Logger LOG = LoggerFactory.getLogger(KeyAction.class);
	private final KeyCombination combination;
	private final KeyRegistry registry;
	private final Runnable runnable;
	private final String description;
	private boolean enabled = true;

	/**
	 * Create a key action.
	 *
	 * @param registry    the registry that owns this action; used by {@link #unregister()} to
	 *                    remove it from the active binding table
	 * @param combination the key gesture that triggers this action
	 * @param runnable    the work to execute when the combination is pressed and the action is enabled
	 * @param description human-readable label shown in the key-help printout
	 */
	public KeyAction(KeyRegistry registry, KeyCombination combination, Runnable runnable, String description) {
		this.registry = registry;
		this.combination = combination;
		this.runnable = runnable;
		this.description = description;
	}

	/**
	 * Execute this action if it is currently enabled.
	 * If disabled, logs at INFO level that the combination was pressed but skipped.
	 */
	public void run() {
    if (enabled) {
      runnable.run();
    }
		else {
	    if (LOG.isInfoEnabled()) {
		    LOG.info("{} is disabled", combination);
	    }
    }
  }

	/**
	 * Remove this action from its owning {@link KeyRegistry}.
	 *
	 * @throws IllegalStateException if the action is no longer registered (e.g. already removed)
	 */
	public void unregister() {
		if (!registry.unregister(combination)) {
			throw new IllegalStateException("No longer registered");
		}
	}

	/**
	 * Returns whether this action will fire when its key combination is pressed.
	 *
	 * @return {@code true} if enabled (the default); {@code false} if suppressed
	 */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * Returns the human-readable label for this action, shown when the key bindings are printed.
	 *
	 * @return the description string supplied at registration; never {@code null}
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Returns the key gesture that triggers this action.
	 *
	 * @return the {@link KeyCombination} bound at registration
	 */
	public KeyCombination getCombination() {
		return combination;
	}

	/**
	 * Enable or disable this action without removing it from the registry.
	 *
	 * @param enabled {@code true} to allow the action to fire; {@code false} to suppress it
	 */
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	@Override
	public int compareTo(KeyAction o) {
		return combination.compareTo(o.combination);
	}
}
