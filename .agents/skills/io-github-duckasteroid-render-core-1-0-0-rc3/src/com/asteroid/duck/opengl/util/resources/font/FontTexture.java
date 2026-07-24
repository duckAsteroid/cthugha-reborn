package com.asteroid.duck.opengl.util.resources.font;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.*;
import java.util.List;

import com.asteroid.duck.opengl.util.geom.Vertice;
import com.asteroid.duck.opengl.util.resources.Resource;
import com.asteroid.duck.opengl.util.resources.buffer.vbo.VertexBufferObject;
import com.asteroid.duck.opengl.util.resources.buffer.vbo.VertexElement;
import com.asteroid.duck.opengl.util.resources.texture.Texture;


/**
 * A wrapper around a glyph-image based font. The glyphs (character images) are stored in a GPU
 * texture, and the various locations + sizes of each letter are described in a map of
 * {@link GlyphData} objects.
 * <p>
 * Based on (but different to)
 * <a href="https://github.com/SilverTiger/lwjgl3-tutorial/blob/master/src/silvertiger/tutorial/lwjgl/text/Font.java">this</a>
 * </p>
 */

public class FontTexture implements Resource {

	/**
	 * Contains the glyph data for each char.
	 */
	private final Map<Character, GlyphData> glyphs;
	/**
	 * Contains the complete font texture.
	 */
	private final Texture texture;
	/**
	 * Pixel height of the font (i.e. tallest glyph)
	 */
	private final int fontHeight;

	/**
	 * Create a font texture from pre-built glyph metrics and the combined glyph strip texture.
	 *
	 * <p>The glyph map is wrapped in an unmodifiable view so callers cannot mutate it after
	 * construction. Font height is derived as the tallest glyph in the map.</p>
	 *
	 * @param glyphs  per-character layout data, keyed by character; must not be empty
	 * @param texture the GPU texture containing all glyph images arranged in a horizontal strip
	 */
	public FontTexture(Map<Character, GlyphData> glyphs, Texture texture) {
		this.glyphs = Collections.unmodifiableMap(glyphs);
		this.texture = texture;
		this.fontHeight = glyphs.values().stream()
						.map(GlyphData::bounds)
						.mapToInt(r -> r.height)
						.max().orElseThrow();
	}

	/**
	 * Look up the glyph metadata for a single character.
	 *
	 * @param c the character to look up
	 * @return the {@link GlyphData} for {@code c}, or {@code null} if the font does not contain it
	 */
	public GlyphData getGlyph(char c) {
		return glyphs.get(c);
	}

	/**
	 * Gets the width in pixels of the specified text.
	 *
	 * @param text The text
	 *
	 * @return Width of text
	 */
	public int getWidth(CharSequence text) {
		int width = 0;
		int lineWidth = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\n') {
				/* Line end, set width to maximum from line width and stored
				 * width */
				width = Math.max(width, lineWidth);
				lineWidth = 0;
				continue;
			}
			if (c == '\r') {
				/* Carriage return, just skip it */
				continue;
			}
			GlyphData g = glyphs.get(c);
			if (g == null) continue;
			lineWidth += g.bounds().width;
		}
		width = Math.max(width, lineWidth);
		return width;
	}

	/**
	 * Gets the height of the specified text.
	 *
	 * @param text The text
	 *
	 * @return Height of text
	 */
	public int getHeight(CharSequence text) {
		int height = 0;
		int lineHeight = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\n') {
				/* Line end, add line height to stored height */
				height += lineHeight;
				lineHeight = 0;
				continue;
			}
			if (c == '\r') {
				/* Carriage return, just skip it */
				continue;
			}
			GlyphData g = glyphs.get(c);
			if (g == null) continue;
			lineHeight = Math.max(lineHeight, g.bounds().height);
		}
		height += lineHeight;
		return height;
	}

	/**
	 * Fill {@code vbo} with origin-relative screen and texture positions for each glyph in {@code text}.
	 * The string baseline datum is at (0, 0) in string space; callers position the string via a model
	 * matrix uniform rather than baking a screen offset into the vertex data.
	 *
	 * @param text      text to render
	 * @param vbo       vertex buffer to write into (must have capacity for {@code text.length() * 4} vertices)
	 * @param screenEl  the {@link VertexElement} that holds the 2D screen position in each vertex
	 * @param texEl     the {@link VertexElement} that holds the 2D texture coordinate in each vertex
	 */
	public void computeVertexData(CharSequence text, VertexBufferObject vbo, VertexElement screenEl, VertexElement texEl) {
		List<Vertice> corners = Vertice.standardFourVertices().toList();
		Point cursor = new Point(0, 0);
		int charIndex = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\n') {
				cursor.x = 0;
				cursor.y += fontHeight;
				continue;
			}
			GlyphData glyph = glyphs.get(c);
			if (glyph == null) continue;
			Rectangle screen = glyph.rawBounds(cursor);
			org.joml.Vector4f tex = glyph.normalBounds();
			for (int j = 0; j < corners.size(); j++) {
				Vertice v = corners.get(j);
				vbo.setElement(charIndex * 4 + j, screenEl, v.from(screen));
				vbo.setElement(charIndex * 4 + j, texEl,    v.from(tex));
			}
			cursor.x += glyph.advance();
			charIndex++;
		}
	}


	@Override
	public void dispose() {
		texture.dispose();
	}


	/**
	 * Returns the pixel height of the tallest glyph in the font.
	 * Use this as the Y advance when wrapping to a new line.
	 *
	 * @return line height in pixels
	 */
	public int getFontHeight() {
		return fontHeight;
	}

	/**
	 * Returns the GPU texture containing the full glyph strip image.
	 * Bind this before issuing draw calls for text rendered with this font.
	 *
	 * @return the OpenGL texture handle; never {@code null} after construction
	 */
	public Texture getTexture() {
		return texture;
	}

	/**
	 * All glyph metadata sorted left-to-right by their X position in the texture strip.
	 * Useful for iterating over the atlas in strip order (e.g. for debugging or atlas export).
	 *
	 * @return an immutable sorted list of all glyph data records
	 */
	public List<GlyphData> glyphs() {
		return glyphs.values().stream().sorted(Comparator.comparingInt(value -> value.bounds().x)).toList();
	}
}
