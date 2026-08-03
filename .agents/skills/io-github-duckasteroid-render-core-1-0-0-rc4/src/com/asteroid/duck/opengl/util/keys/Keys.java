package com.asteroid.duck.opengl.util.keys;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Singleton registry of all GLFW keyboard keys and modifiers, populated at class-load time
 * by reflecting over the {@link org.lwjgl.glfw.GLFW} public fields.
 *
 * <p>Key constants (e.g. {@code GLFW_KEY_A}) are stored indexed by both their integer code and
 * their derived name ({@code "A"}). Modifier constants (e.g. {@code GLFW_MOD_SHIFT}) are stored
 * in a separate map to support bitmask decomposition via {@link #modsFor(int)}.</p>
 *
 * <p>Call {@link #instance()} to obtain the singleton; the maps are populated once at startup
 * and are read-only thereafter.</p>
 */
public class Keys {
	private static final Logger LOG = LoggerFactory.getLogger(Keys.class);

	private final HashMap<Integer, Key> keysByCode = new HashMap<>();
	private final HashMap<String, Key> keysByName = new HashMap<>();
	private final HashMap<Integer, Key> modsByCode = new HashMap<>();
	private final HashMap<String, Key> modsByName = new HashMap<>();

	/**
	 * Returns the application-wide singleton instance.
	 *
	 * @return the shared {@link Keys} registry; never {@code null}
	 */
	public static Keys instance() {
		return SINGLETON;
	}

	private static Keys SINGLETON;

	static {
		try {
			SINGLETON = new Keys();
		} catch (IllegalAccessException e) {
			LOG.error("Error loading keys", e);
		}
	}

	private Keys() throws IllegalAccessException {
		final Set<String> ignored = Set.of("GLFW_KEY_LAST");
		List<Key> ordinaryKeys = keysFromGL("GLFW_KEY_")
						.filter(f -> !ignored.contains(f.getName()))
						.map(f -> fromField(f, "GLFW_KEY_", false))
						.filter(Optional::isPresent).map(Optional::get).toList();
		for (Key key : ordinaryKeys) {
			if (!keysByCode.containsKey(key.code())) {
				keysByCode.put(key.code(), key);
				keysByName.put(key.name(), key);
			} else {
				LOG.warn("Key "+key+" already mapped to "+keysByCode.get(key.code()));
			}
		}

		List<Key> modifiers = keysFromGL("GLFW_MOD_")
						.map(f -> fromField(f, "GLFW_MOD_", true))
						.filter(Optional::isPresent).map(Optional::get).toList();
		for (Key key : modifiers) {
			if (!modsByCode.containsKey(key.code())) {
				modsByCode.put(key.code(), key);
				modsByName.put(key.name(), key);
			} else {
				LOG.warn("Modifier Key "+key+" already mapped to "+modsByCode.get(key.code()));
			}
		}
	}
	private static Optional<Key> fromField(Field f, String startsWith, boolean isModifier) {
		try {
			String keyName = f.getName().substring(startsWith.length());
			Integer keyValue = (Integer) f.get(null);
			return Optional.of(new Key(keyValue, keyName, isModifier));
		} catch (IllegalAccessException e) {
			LOG.warn("Unable to load key from field:"+f.getName(), e);
			return Optional.empty();
		}
	}
	private static Stream<Field> keysFromGL(String startsWith) {
		return Arrays.stream(GLFW.class.getFields()).filter(f -> f.getName().startsWith(startsWith));
	}

	/**
	 * Look up a non-modifier key by its GLFW integer code.
	 *
	 * @param key the GLFW key code (e.g. {@code GLFW_KEY_A})
	 * @return the matching {@link Key}, or empty if the code is not a registered non-modifier key
	 */
	public Optional<Key> keyFor(int key) {
		return Optional.ofNullable(keysByCode.get(key));
	}

	/**
	 * Decompose a GLFW modifier bitmask into the individual modifier {@link Key} objects that
	 * are active.
	 *
	 * <p>Works by checking each registered modifier's code against the bitmask; only modifiers
	 * whose bits are all set in {@code mods} are included.</p>
	 *
	 * @param mods the GLFW modifier bitmask from the key callback (e.g. {@code GLFW_MOD_SHIFT | GLFW_MOD_CONTROL})
	 * @return the set of active modifier keys; empty if no modifier bits are set
	 */
	public Set<Key> modsFor(int mods) {
		return modsByCode.values().stream()
						.filter(k -> (k.code() & mods) == k.code())
						.collect(Collectors.toSet());
	}

	/**
	 * Look up a non-modifier key by an upper-case ASCII letter.
	 *
	 * <p>GLFW key codes for letters match their ASCII codepoints, so looking up {@code 'A'}
	 * is equivalent to {@code keyFor((int)'A')}.</p>
	 *
	 * @param key an upper-case ASCII letter ({@code 'A'}–{@code 'Z'})
	 * @return the corresponding {@link Key}
	 * @throws IllegalArgumentException if {@code key} is not an upper-case alphabetic character
	 */
	public Key keyFor(char key) {
		if (Character.isAlphabetic(key) && Character.isUpperCase(key)) {
			return keysByCode.get((int)key);
		}
		else throw new IllegalArgumentException("Invalid simple key");
	}

	/**
	 * Look up a key (ordinary or modifier) by its derived name string.
	 *
	 * <p>Searches ordinary keys first, then modifiers, so a name that appears in both (unlikely
	 * but possible after GLFW changes) returns the ordinary key.</p>
	 *
	 * @param name the key name as stored in {@link Key#name()} (e.g. {@code "A"}, {@code "SHIFT"})
	 * @return the matching {@link Key}, or {@code null} if no key with that name exists
	 */
	public Key keyForName(String name) {
		if(!keysByName.containsKey(name)) {
			return modsByName.get(name);
		}
		return keysByName.get(name);
	}
}
