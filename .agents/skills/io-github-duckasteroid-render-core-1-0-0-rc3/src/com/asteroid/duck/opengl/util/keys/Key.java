package com.asteroid.duck.opengl.util.keys;

/**
 * An immutable descriptor for a single keyboard key or modifier key, derived from GLFW constants.
 *
 * <p>Keys are loaded at startup by {@link Keys} via reflection over {@link org.lwjgl.glfw.GLFW}
 * field names. The {@link #modifier} flag distinguishes modifier keys (Shift, Ctrl, Alt, Super)
 * from ordinary keys; this distinction is used by {@link KeyCombination} when building
 * hotkey definitions.</p>
 *
 * @param code     the GLFW integer key code (e.g. {@code GLFW_KEY_A}, {@code GLFW_MOD_SHIFT})
 * @param name     the human-readable name derived from the GLFW constant (e.g. {@code "A"}, {@code "SHIFT"})
 * @param modifier {@code true} if this key acts as a modifier ({@code GLFW_MOD_*}); {@code false} for regular keys
 */
public record Key(int code, String name, boolean modifier) implements Comparable<Key> {
	@Override
	public int compareTo(Key o) {
		int result = Boolean.compare(this.modifier, o.modifier);
		if (result == 0) {
			result = code - o.code;
		}
		return result;
	}
}
