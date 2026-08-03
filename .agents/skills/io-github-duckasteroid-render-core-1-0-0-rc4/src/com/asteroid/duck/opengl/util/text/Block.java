package com.asteroid.duck.opengl.util.text;

/**
 * Utility for splitting a block of text into individual lines.
 *
 * <p>A lightweight helper used when text content needs to be broken into lines before being
 * passed to a renderer that works line-by-line (e.g. {@link StringRenderer}).</p>
 */
public class Block {

    /** Not instantiable — all methods are static. */
    private Block() {}

    /**
     * Split a string on newline characters into an array of individual lines.
     * Empty lines (e.g. from consecutive {@code '\n'} characters) are preserved.
     *
     * @param text the text to split; must not be {@code null}
     * @return array of lines in the order they appear in {@code text}; never {@code null}
     */
	public static String[] lines(String text) {
		return text.split("\n");
	}
}
