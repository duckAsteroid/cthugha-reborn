package com.asteroid.duck.opengl.util.resources.font.factory;

import com.asteroid.duck.opengl.util.resources.font.GlyphData;
import com.asteroid.duck.opengl.util.resources.font.Padding;
import org.joml.Vector2f;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * An individual generated glyph image with glyph location details (relative to the image)
 *
 * @param image  the BufferedImage (normally MUCH larger than the actual Glyph)
 * @param datum  the AWT Point coordinates of the character datum in the image
 * @param bounds the drawn area of the glyph in the image
 */
public record GlyphImage(BufferedImage image, Point datum, Rectangle bounds) {

	/**
	 * The offset of the datum within the bounds rectangle
	 * @return a bounding rectangle relative offset
	 */
	public Point boundsRelativeDatum() {
		return new Point(datum.x - bounds.x, datum.y - bounds.y);
	}

	/**
	 * A rectangle that encompasses the bounds AND the datum
	 *
	 * @return the bounds rect
	 */
	public Rectangle extent() {
		Rectangle extent = new Rectangle(bounds);
		extent.add(new Rectangle(datum, new Dimension(1,1)));
		return extent;
	}


	/**
	 * Render this glyph image into the font image strip and return its layout metadata.
	 *
	 * <p>The glyph is placed at {@code x + padding.left()} horizontally and {@code padding.top()}
	 * vertically within the strip. The returned {@link GlyphData} records the destination rectangle
	 * and normalised texture coordinates so the glyph can later be recovered for text rendering.</p>
	 *
	 * @param padding   inset applied to all four sides of the glyph placement within the strip
	 * @param x         the left edge of this glyph's slot in the strip, in pixels (before padding)
	 * @param g         the {@link Graphics2D} context of the combined strip image to draw into
	 * @param imageSize the full pixel dimensions of the strip image, used to compute normalised UV coords
	 * @return the glyph's position, datum offset, and normalised texture bounds in the strip
	 */
	public GlyphData renderToStrip(Padding padding, int x, Graphics2D g, Dimension imageSize) {
		Rectangle destination = new Rectangle(x + padding.left(), padding.top(), bounds.width, bounds.height);
		ImageRenderer renderer = new ImageRenderer(g, image);
		renderer.drawImage(bounds, destination);
		return new GlyphData(boundsRelativeDatum(), destination, GlyphData.normalBounds(destination, new Vector2f(imageSize.width, imageSize.height)));
	}

}
