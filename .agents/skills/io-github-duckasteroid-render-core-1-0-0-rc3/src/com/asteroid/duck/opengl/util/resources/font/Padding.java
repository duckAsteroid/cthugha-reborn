package com.asteroid.duck.opengl.util.resources.font;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * A record that represents pixel padding added to a rectangle
 * @param left padding to the left
 * @param top padding above
 * @param right padding to the right
 * @param bottom padding below
 */
public record Padding(int left, int top, int right, int bottom) {
	/**
	 * The horizontal extent of the padding
	 * @return left + right
	 */
	public int width() {
		return right + left;
	}

	/**
	 * The vertical extent of the padding
	 * @return bottom + top
	 */
	public int height() {
		return bottom + top;
	}

	/**
	 * Given a rectangle - add this padding and return the resulting rectangle
	 * @param src the original rectangle
	 * @return a rectangle of the source, with padding added as described by this
	 */
	public Rectangle expand(Rectangle src) {
		return new Rectangle(src.x - left, src.y - top, src.width + width(), src.height + height());
	}
}
