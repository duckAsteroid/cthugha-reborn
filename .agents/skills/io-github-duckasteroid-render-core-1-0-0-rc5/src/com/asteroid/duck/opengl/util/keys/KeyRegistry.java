package com.asteroid.duck.opengl.util.keys;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Stream;

/**
 * Maintains the mapping from {@link KeyCombination} to {@link KeyAction} and dispatches
 * incoming GLFW key events to the correct registered handler.
 *
 * <p>Actions are stored in two structures: a {@link java.util.HashMap} for O(1) dispatch and a
 * {@link java.util.LinkedList} that preserves insertion order for ordered iteration (used when
 * printing the key-help table). Each combination may only be registered once; attempting to
 * register a duplicate throws {@link IllegalArgumentException}.</p>
 */
public class KeyRegistry implements Iterable<KeyAction> {
	private static final Logger LOG = LoggerFactory.getLogger(KeyRegistry.class);

	private final Map<KeyCombination, KeyAction> keyActions = new HashMap<>();
	private final LinkedList<KeyAction> orderedActions = new LinkedList<>();

    /** Default constructor; instances are created by {@link com.asteroid.duck.opengl.util.GLWindow}. */
    public KeyRegistry() {}

	/**
	 * Register an action triggered by a single unmodified GLFW key code.
	 *
	 * @param key         GLFW key constant (e.g. {@code GLFW_KEY_F})
	 * @param runnable    the action to run when the key is pressed
	 * @param description human-readable label for the key-help printout
	 * @throws IllegalArgumentException if the key code is not recognised or already registered
	 */
	public void registerKeyAction(int key, Runnable runnable, String description) {
		Key knownKey = Keys.instance().keyFor(key).orElseThrow(() -> new IllegalArgumentException("Unknown key code: " + key));
		registerKeyAction(new KeyCombination(Collections.singleton(knownKey), Collections.emptySet()), runnable, description);
	}
	/**
	 * Register an action triggered by a key code combined with a GLFW modifier bitmask.
	 *
	 * @param key         GLFW key constant for the primary key
	 * @param mod         GLFW modifier bitmask (e.g. {@code GLFW_MOD_SHIFT | GLFW_MOD_CONTROL})
	 * @param runnable    the action to run when the gesture is pressed
	 * @param description human-readable label for the key-help printout
	 * @throws IllegalArgumentException if either key code is unknown or the combination is already taken
	 */
	public void registerKeyAction(int key, int mod, Runnable runnable, String description) {
		Key knownKey = Keys.instance().keyFor(key).orElseThrow(() -> new IllegalArgumentException("Unknown key code: " + key));
		Set<Key> knownMods = Keys.instance().modsFor(mod);
		registerKeyAction(new KeyCombination(Collections.singleton(knownKey), knownMods), runnable, description);
	}

	/**
	 * Register the same action for multiple key combinations.
	 * Each combination is registered independently; all map to the same {@code runnable}.
	 *
	 * @param keyCombinations the set of key gestures that trigger the action
	 * @param runnable        the action to run for any matching gesture
	 * @param description     human-readable label shared across all combinations
	 */
	public void registerKeyAction(Set<KeyCombination> keyCombinations, Runnable runnable, String description) {
		for(KeyCombination combo : keyCombinations) {
			registerKeyAction(combo, runnable, description);
		}
	}

	/**
	 * Register an action for a pre-built {@link KeyCombination} and return the resulting handle.
	 *
	 * @param combo       the key gesture to bind
	 * @param runnable    the action to run when the gesture fires
	 * @param description human-readable label; substituted with {@code "Unknown"} if {@code null}
	 * @return the created {@link KeyAction}; hold the reference if you need to later
	 *         {@link KeyAction#unregister()} or {@link KeyAction#setEnabled(boolean) disable} it
	 * @throws IllegalArgumentException if {@code combo} is already registered
	 */
	public KeyAction registerKeyAction(KeyCombination combo, Runnable runnable, String description) {
		if (keyActions.containsKey(combo)) {
			throw new IllegalArgumentException("Action already registered for keys="+combo.asSimpleString());
		}
		if (description == null) {
			description = "Unknown";
		}
		KeyAction keyAction = new KeyAction(this, combo, runnable, description);
		keyActions.put(combo, keyAction);
		orderedActions.add(keyAction);
		return keyAction;
	}

	boolean unregister(KeyCombination combo) {
		return keyActions.remove(combo) != null;
	}

	/**
	 * Dispatch a key-press event to the registered action for {@code combo}, if any.
	 * Called by the GLFW key callback on the render thread; unknown combinations are silently ignored.
	 *
	 * @param combo the key gesture derived from the GLFW event
	 */
	public void handleCallback(KeyCombination combo) {
		if (keyActions.containsKey(combo)) {
			KeyAction action = keyActions.get(combo);
			action.run();
		}
	}

	@Override
	public Iterator<KeyAction> iterator() {
		return stream().iterator();
	}

	/**
	 * Stream all registered actions in insertion order.
	 *
	 * @return an ordered stream of all currently registered actions
	 */
	public Stream<KeyAction> stream() {
		return orderedActions.stream();
	}
}
