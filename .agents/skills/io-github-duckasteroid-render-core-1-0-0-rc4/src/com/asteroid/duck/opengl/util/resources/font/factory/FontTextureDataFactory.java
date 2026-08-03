package com.asteroid.duck.opengl.util.resources.font.factory;

import com.asteroid.duck.opengl.util.resources.font.GlyphData;
import com.asteroid.duck.opengl.util.resources.font.Padding;
import com.asteroid.duck.opengl.util.resources.texture.DataFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A factory for {@link FontTextureData} - the first step in using font textures.
 * Basically, this uses AWT to render fonts onto images and calculate metrics for each glyph.
 */
public class FontTextureDataFactory {
	private static final Logger LOG = LoggerFactory.getLogger(FontTextureDataFactory.class);

	private final java.awt.Font font;
	private final boolean antialias;
	private final Padding padding;
	private FontMetrics fontMetrics;
	/**
	 * If non-{@code null}, the combined glyph strip image is written as {@code font.png} under
	 * this directory after each {@link #createFontTextureData()} call. Useful for verifying glyph
	 * layout and atlas packing during development.
	 */
	public Path imageDumpPath;

	/**
	 * When {@code true}, a coloured border is drawn around each glyph's destination rectangle in
	 * the combined strip. Useful for diagnosing padding/spacing issues.
	 */
	public boolean debugBoundary = false;

	/**
	 * When {@code true}, the background of the combined strip is filled with a distinctive colour
	 * instead of transparency. Useful for confirming that glyph images do not bleed outside their
	 * allocated slots.
	 */
	public boolean debugBackground = false;

	/**
	 * Create a factory for the given font and anti-aliasing preference.
	 *
	 * <p>Padding is derived automatically from the font's point size ({@link #padding(int)}),
	 * ensuring a proportional margin around each glyph regardless of font size.</p>
	 *
	 * @param font      the AWT font to rasterise; point size determines glyph dimensions and padding
	 * @param antialias {@code true} to enable anti-aliased rendering via
	 *                  {@link java.awt.RenderingHints#VALUE_ANTIALIAS_ON}
	 */
	public FontTextureDataFactory(java.awt.Font font, boolean antialias) {
		this.font = font;
		this.padding = padding(font.getSize());
		this.antialias = antialias;
	}

	/**
	 * Compute the per-glyph padding from a font point size.
	 *
	 * <p>The padding is 5% of the font size (minimum 1 px) applied equally on all four sides.
	 * This keeps glyphs from visually touching each other in the atlas strip while avoiding
	 * wasteful blank space at smaller sizes.</p>
	 *
	 * @param size the font point size
	 * @return a symmetric {@link Padding} appropriate for glyphs of this size
	 */
	public static Padding padding(int size) {
		int pad = Math.max(1, (5 * size) / 100);
		return new Padding(pad, pad, pad, pad);
	}


	/**
	 * Returns a stream of all printable ASCII and Latin-1 characters (U+0020–U+00FF, excluding
	 * U+007F DEL).
	 *
	 * <p>Pass this to {@link #createFontTextureData()} indirectly by iterating the stream to
	 * choose which characters to include in the atlas.</p>
	 *
	 * @return a stream of 223 characters covering the standard printable range
	 */
	public Stream<Character> completeCharacterSet() {
		return IntStream.range(32, 256).filter(i -> i != 127).mapToObj(i -> (char)i);
	}



	/**
	 * Render all printable characters from {@link #completeCharacterSet()} into a single
	 * horizontal image strip and return the resulting atlas data.
	 *
	 * <p>Each character is rasterised at the configured font size with AWT, then packed
	 * left-to-right into a strip image. Glyph bounds are recorded in both pixel and normalised
	 * coordinates so the atlas can be uploaded as a GL texture and sampled by a text shader.</p>
	 *
	 * <p>If {@link #imageDumpPath} is set, the combined strip is written to disk as a PNG for
	 * inspection.</p>
	 *
	 * @return the atlas data including per-glyph metrics, individual glyph images, and the
	 *         combined strip image; ready to pass to {@link com.asteroid.duck.opengl.util.resources.font.FontTextureFactory}
	 */
	public FontTextureData createFontTextureData() {
		FontMetrics metrics = getFontMetrics();
		// create a map of each character as an image containing it
		Map<Character, GlyphImage> glyphImages = new HashMap<>();
		int maxHeight = 0;
		int totalWidth = 0;
		for(Character c : completeCharacterSet().toList()) {
			GlyphImage charImage = createCharacterImage(c);
			/* If char image is null that font does not contain the char */
			if (charImage != null) {
				glyphImages.put(c, charImage);
				Rectangle rect = charImage.bounds();
				rect = padding.expand(rect);
				if (rect.height > maxHeight) {
					maxHeight = rect.height;
				}
				totalWidth += rect.width;
			}
		}
		// now create a single image out of the characters and glyph data
		Map<Character, GlyphData> glyphData = new HashMap<>();
		Dimension imageDims = new Dimension(totalWidth, maxHeight);
		BufferedImage image = newImage(imageDims);
		Graphics2D g = image.createGraphics();

		// now draw each character in a line
		int x = 0;
		for(Character c : glyphImages.keySet()) {
			GlyphImage gi = glyphImages.get(c);

			GlyphData gData = gi.renderToStrip(padding, x, g, imageDims);
			glyphData.put(c, gData);
			x += gData.bounds().width + padding.right();
		}
		// dump the image for debug
		if(imageDumpPath != null) {
			try {
				Files.createDirectories(imageDumpPath);
				ImageIO.write(image, "png", Files.newOutputStream(imageDumpPath.resolve("font.png")));
			} catch (IOException e) {
				LOG.error("Failed to dump image", e);
			}
		}
		return new FontTextureData(glyphImages, glyphData, image );
	}

	/**
	 * Create a glyph image (image with data) for a given character
	 * @param c the character
	 * @return a glyph image (if the character renders) or null
	 */
	public GlyphImage createCharacterImage(char c) {
		FontMetrics metrics = getFontMetrics();
		/* Get char charWidth */
		int charWidth = metrics.charWidth(c);
		/* Check if charWidth is 0 */
		if (charWidth == 0) {
			return null;
		}

		final String charStr = String.valueOf(c);
		/* Create image for holding the char */
		// this is not using the native storage raster buffer
		// as it's just going to be painted onto the actual image
		int imageWidth = metrics.getMaxAdvance() + padding.width();
		int imageHeight = metrics.getMaxAscent() + metrics.getMaxDescent() + padding.height();
		// the baseline is enough for the biggest font ascent plus the padding
		// it is the same for all glyphs in the font
		int baseline = metrics.getMaxAscent() + padding.top();
		BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);

		Graphics2D g = image.createGraphics();
		if (antialias) {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		}
		g.setFont(font);
		g.setPaint(java.awt.Color.WHITE);
		// the position for the glyph to be rendered onto the image
		int x = padding.left();
		int y = baseline;
		g.drawString(charStr, x, y);

		// find the bounding box
		Rectangle bounds = findPixelBounds(image, 1);
		if (bounds.width < 0 || bounds.height < 0) {
			// we did not have any pixel bounds (empty image - e.g. space character)
			// make some up using metrics
			int width = metrics.charWidth(c);
			bounds = new Rectangle(x, y, width, 1);
		}
		g.dispose();
		return new GlyphImage(image, new Point(x, y), bounds);
	}

	/**
	 * Attempts to find the smallest rectangle in an image (from the border)
	 * that contains some opaque pixel data (according to some alpha threshold).
	 * @param image the image to search
	 * @param alphaThreshold the threshold of transparency
	 * @return the rectangle in the image outside which is only transparent pixels
	 */
	private Rectangle findPixelBounds(BufferedImage image, int alphaThreshold) {
		int left = image.getWidth(), right = 0, top = image.getHeight(), bottom = 0;
		for(int y = 0; y < image.getHeight(); y++) {
			for(int x = 0; x < image.getWidth(); x++) {
				int rgbaColor = image.getRGB(x, y);
				//int red = (rgbaColor >> 24) & 0xFF;
				//int green = (rgbaColor >> 16) & 0xFF;
				//int blue = (rgbaColor >> 8) & 0xFF;
				int alpha = rgbaColor & 0xFF;

				if (alpha >= alphaThreshold) {
					left = Math.min(left, x);
					right = Math.max(right, x);
					top = Math.min(top, y);
					bottom = Math.max(bottom, y);
				}
			}
		}
		return new Rectangle(left, top, right - left + 1, bottom - top + 1);
	}

	private BufferedImage newImage(Dimension size) {
		return DataFormat.RGBA.apply(size);
	}

	private FontMetrics getFontMetrics() {
		if (fontMetrics == null) {
			BufferedImage tmp = newImage(new Dimension(1,1));
			Graphics2D g = tmp.createGraphics();
			if (antialias) {
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			}
			g.setFont(font);
			fontMetrics = g.getFontMetrics();
			g.dispose();
		}
		return fontMetrics;
	}
}
