package com.asteroid.duck.opengl.util.keys;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An immutable description of a keyboard gesture: one or more non-modifier keys held together
 * with zero or more modifier keys (Shift, Ctrl, Alt, Super).
 *
 * <p>Used as the trigger in {@link KeyAction} registrations. {@link KeyRegistry} matches incoming
 * GLFW key events against registered combinations to dispatch the correct action.</p>
 *
 * @param keys      the set of non-modifier keys that must be pressed; typically a singleton
 * @param modifiers the modifier keys that must be active simultaneously; may be empty
 */
public record KeyCombination(Set<Key> keys, Set<Key> modifiers) implements Comparable<KeyCombination> {
	/**
	 * Create a combination for a single unmodified alphabetic key.
	 *
	 * @param key an upper-case ASCII letter (e.g. {@code 'A'}); must be recognised by
	 *            {@link Keys#keyFor(char)}
	 * @return a {@link KeyCombination} with no modifiers
	 */
	public static KeyCombination simple(char key) {
		Key keyed = Keys.instance().keyFor(key);
		return new KeyCombination(Set.of(keyed), Collections.emptySet());
	}

	/**
	 * Create a combination for a single unmodified key looked up by its GLFW-derived name.
	 *
	 * <p>Use this for non-alphabetic keys whose names come from GLFW constants (strip the
	 * {@code GLFW_KEY_} prefix), e.g. {@code "LEFT_BRACKET"}, {@code "RIGHT_BRACKET"},
	 * {@code "PERIOD"}, {@code "SLASH"}.</p>
	 *
	 * @param name the GLFW-derived key name as recognised by {@link Keys#keyForName(String)}
	 * @return a {@link KeyCombination} with no modifiers
	 * @throws NullPointerException if no key with that name exists
	 */
	public static KeyCombination named(String name) {
		Key keyed = Keys.instance().keyForName(name);
		return new KeyCombination(Set.of(keyed), Collections.emptySet());
	}

	/**
	 * Create a combination for a single alphabetic key held with one or more named modifier keys.
	 *
	 * @param key  an upper-case ASCII letter
	 * @param mods GLFW modifier names (e.g. {@code "SHIFT"}, {@code "CONTROL"}) as understood by
	 *             {@link Keys#keyForName(String)}
	 * @return a {@link KeyCombination} matching the key pressed with all listed modifiers
	 */
	public static KeyCombination simpleWithMods(char key, String ... mods) {
		Key keyed = Keys.instance().keyFor(key);
		Set<Key> modSet = Arrays.stream(mods).map(m -> Keys.instance().keyForName(m)).collect(Collectors.toSet());
		return new KeyCombination(Set.of(keyed), modSet);
	}

	/**
	 * Create a combination for a named key held with one or more named modifier keys.
	 *
	 * @param name the GLFW-derived key name (e.g. {@code "PRINT_SCREEN"})
	 * @param mods GLFW modifier names (e.g. {@code "SHIFT"}, {@code "CONTROL"})
	 * @return a {@link KeyCombination} matching the key pressed with all listed modifiers
	 */
	public static KeyCombination namedWithMods(String name, String... mods) {
		Key keyed = Keys.instance().keyForName(name);
		Set<Key> modSet = Arrays.stream(mods).map(m -> Keys.instance().keyForName(m)).collect(Collectors.toSet());
		return new KeyCombination(Set.of(keyed), modSet);
	}

	/**
	 * Format this combination as a compact human-readable string suitable for console output.
	 *
	 * <p>Example: {@code "A[SHIFT|CONTROL]"} for Ctrl+Shift+A, or just {@code "F"} for an
	 * unmodified key.</p>
	 *
	 * @return the formatted combination string; non-empty
	 */
	public String asSimpleString() {
		String mods = "";
		if (!modifiers.isEmpty()) {
			mods = modifiers.stream().map(Key::name).collect(Collectors.joining("|", "[", "]"));
		}
		String keystr = keys.stream().map(Key::name).collect(Collectors.joining("+"));
		return keystr + mods;
	}

	@Override
	public int compareTo(KeyCombination o) {
		Iterator<Key> thisIterator = this.keys.iterator();
		Iterator<Key> otherIterator = o.keys.iterator();

		while (thisIterator.hasNext() && otherIterator.hasNext()) {
			Key thisKey = thisIterator.next();
			Key otherKey = otherIterator.next();
			int comparison = thisKey.compareTo(otherKey);
			if (comparison != 0) {
				return comparison;
			}
		}

		// If all elements are equal, compare the sizes
		return Integer.compare(this.keys.size(), o.keys.size());
	}
}
