package com.asteroid.duck.opengl.util.resources.font;

import org.joml.Vector2f;
import org.joml.Vector4f;

import java.awt.*;

/**
 * Data about a single glyph in a font image/texture.
 * The datum represents the origin of the glyph when drawing it. Typically this is on the 'baseline'
 * of the font.
 * The bounds represent the AWT pixel rectangle in the image strip that contains the glyph image data
 * The normalBounds are the OpenGL texture coordinates for the glyph (0-1 range) in the texture strip.
 * @param datumOffset The offset to the datum within the image bounds
 * @param bounds The AWT bounds of the image for the glyph (in the strip texture)
 * @param normalBounds The normalised (0-1) OpenGL bounds of the glyph in the texture strip (as used in OpenGL).
 */
public record GlyphData(Point datumOffset, Rectangle bounds, Vector4f normalBounds) {
	/**
	 * How many pixels to advance the cursor after drawing this glyph.
	 * @return the advance for this glyph in pixels
	 */
	public int advance() {
		int advance = bounds.width;
		advance -= datumOffset.x;
		return advance;
	}

	/**
	 * Convert AWT pixel bounds to normalised OpenGL texture coordinates (UV space).
	 *
	 * <p>AWT uses a top-left origin; OpenGL uses a bottom-left origin. This method inverts
	 * the Y axis so that the returned vector works correctly with standard GL texture sampling.</p>
	 *
	 * @param bounds          the pixel rectangle of the glyph in the combined image strip
	 * @param imageDimensions the full pixel dimensions of the strip texture (width, height)
	 * @return a {@code vec4(s_min, t_min, s_max, t_max)} ready for upload as a shader uniform
	 */
	public static Vector4f normalBounds(Rectangle bounds, Vector2f imageDimensions) {
		// 1. Calculate the pixel coordinates for the sub-rectangle's corners.
		//    AWT coordinates:
		//    top-left: (bounds.x, bounds.y)
		//    bottom-right: (bounds.x + bounds.width, bounds.y + bounds.height)

		// 2. Normalize X-coordinates (s-coordinates in OpenGL)
		//    s_min corresponds to bounds.x
		//    s_max corresponds to bounds.x + bounds.width
		float s_min = bounds.x / imageDimensions.x;
		float s_max = (bounds.x + bounds.width) / imageDimensions.x;

		// 3. Normalize Y-coordinates (t-coordinates in OpenGL) and invert for OpenGL's bottom-left origin.
		//    In AWT:
		//    The top edge of the bounds is at bounds.y from the image's top.
		//    The bottom edge of the bounds is at bounds.y + bounds.height from the image's top.

		//    To get OpenGL's pixel Y (from bottom):
		//    OpenGL pixel Y for AWT's top edge = imageSize.height - bounds.y
		//    OpenGL pixel Y for AWT's bottom edge = imageSize.height - (bounds.y + bounds.height)

		//    Since t_min is the bottom of the region and t_max is the top:
		float t_min = (bounds.y + bounds.height) / imageDimensions.y;
		float t_max =  bounds.y / imageDimensions.y;

		// Create and return the Vector4f
		return new Vector4f(s_min, t_min, s_max, t_max);
	}

	/**
	 * Compute the screen-space pixel rectangle where this glyph should be drawn,
	 * given a cursor position in string-layout space.
	 *
	 * <p>The datum offset is subtracted so the glyph aligns its baseline/origin at {@code pos}
	 * rather than its top-left corner.</p>
	 *
	 * @param pos the string cursor position (typically the baseline origin); {@code null} treated as (0, 0)
	 * @return the pixel rectangle for this glyph relative to {@code pos}
	 */
	public Rectangle rawBounds(Point pos) {
		if (pos == null) {
			pos = new Point(0, 0);
		}
		// The bounds are relative to the datum offset, so we need to adjust the position
		return new  Rectangle(pos.x - datumOffset().x, pos.y - datumOffset().y, bounds.width, bounds.height);
	}

	/**
	 * Compute the absolute screen-space position of this glyph's datum (baseline origin)
	 * given a cursor position.
	 *
	 * @param pos the string cursor position; {@code null} treated as (0, 0)
	 * @return the datum position as a float vector for use in shader uniforms
	 */
	public Vector2f datum(Point pos) {
		if (pos == null) {
			pos = new Point(0, 0);
		}
		// The bounds are relative to the datum offset, so we need to adjust the position
		return new Vector2f((float)(pos.x - datumOffset().x), (float)(pos.y - datumOffset().y));
	}
}
