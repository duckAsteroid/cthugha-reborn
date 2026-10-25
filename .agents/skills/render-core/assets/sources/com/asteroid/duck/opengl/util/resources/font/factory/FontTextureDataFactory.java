package com.asteroid.duck.opengl.util.resources.font.factory;

import com.asteroid.duck.opengl.util.resources.font.GlyphData;
import com.asteroid.duck.opengl.util.resources.font.Padding;
import com.asteroid.duck.opengl.util.resources.texture.DataFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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
	/** Reused by {@link #createCharacterImage} for every glyph; sized for the font's largest. */
	private BufferedImage scratch;
	private Graphics2D scratchGraphics;
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
	 * Create a glyph image (image with data) for a given character.
	 *
	 * <p>The glyph is drawn into a scratch image reused for every glyph of this factory, then
	 * cropped to its drawn pixels, so the returned image is exactly {@link GlyphImage#bounds()}
	 * in size and the bounds start at 0,0.</p>
	 *
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

		BufferedImage image = scratchImage();
		int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		Arrays.fill(pixels, 0); // clear the previous glyph
		// the baseline is enough for the biggest font ascent plus the padding
		// it is the same for all glyphs in the font
		int baseline = metrics.getMaxAscent() + padding.top();
		// the position for the glyph to be rendered onto the image
		int x = padding.left();
		int y = baseline;
		scratchGraphics.drawString(String.valueOf(c), x, y);

		// find the bounding box
		Rectangle bounds = findPixelBounds(pixels, image.getWidth(), image.getHeight(), 1);
		if (bounds.width < 0 || bounds.height < 0) {
			// we did not have any pixel bounds (empty image - e.g. space character)
			// make some up using metrics
			int width = metrics.charWidth(c);
			bounds = new Rectangle(x, y, width, 1);
		}
		// crop to the bounds; the scratch image is overwritten by the next glyph
		BufferedImage glyph = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
		int[] glyphPixels = ((DataBufferInt) glyph.getRaster().getDataBuffer()).getData();
		int copyWidth = Math.min(bounds.width, image.getWidth() - bounds.x);
		for (int row = 0; row < bounds.height && bounds.y + row < image.getHeight(); row++) {
			System.arraycopy(pixels, (bounds.y + row) * image.getWidth() + bounds.x,
					glyphPixels, row * bounds.width, Math.max(copyWidth, 0));
		}
		return new GlyphImage(glyph, new Point(x - bounds.x, y - bounds.y),
				new Rectangle(0, 0, bounds.width, bounds.height));
	}

	private BufferedImage scratchImage() {
		if (scratch == null) {
			FontMetrics metrics = getFontMetrics();
			int imageWidth = metrics.getMaxAdvance() + padding.width();
			int imageHeight = metrics.getMaxAscent() + metrics.getMaxDescent() + padding.height();
			scratch = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
			scratchGraphics = scratch.createGraphics();
			if (antialias) {
				scratchGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			}
			scratchGraphics.setFont(font);
			scratchGraphics.setPaint(java.awt.Color.WHITE);
		}
		return scratch;
	}

	/**
	 * Attempts to find the smallest rectangle in an image (from the border)
	 * that contains some opaque pixel data (according to some alpha threshold).
	 * @param argb the image's packed ARGB pixels, row by row
	 * @param width the image width
	 * @param height the image height
	 * @param alphaThreshold the threshold of transparency
	 * @return the rectangle in the image outside which is only transparent pixels
	 */
	private static Rectangle findPixelBounds(int[] argb, int width, int height, int alphaThreshold) {
		int left = width, right = 0, top = height, bottom = 0;
		for(int y = 0; y < height; y++) {
			for(int x = 0; x < width; x++) {
				int alpha = argb[y * width + x] >>> 24;
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
