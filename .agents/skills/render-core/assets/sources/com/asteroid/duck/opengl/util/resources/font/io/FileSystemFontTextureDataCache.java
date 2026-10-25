package com.asteroid.duck.opengl.util.resources.font.io;


import com.asteroid.duck.opengl.util.resources.font.GlyphData;
import com.asteroid.duck.opengl.util.resources.font.factory.FontTextureData;
import com.asteroid.duck.opengl.util.resources.font.factory.GlyphImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Provides a cache for font texture data stored in the file system.
 * It creates folder names based on the font name and style.
 * Within this folder it stores:
 * <ul>
 *   <li>A combined glyph image "strip"</li>
 *   <li>A folder containing the individual glyph images</li>
 *   <li>A folder containing a data file for each glyph that contains the bounds and datum</li>
 * </ul>
 * @see FontTextureData
 */
public class FileSystemFontTextureDataCache {
	private static final Logger LOG = LoggerFactory.getLogger(FileSystemFontTextureDataCache.class);

	private final Path root;

	/**
	 * Create a cache that stores and retrieves font texture data under the given root directory.
	 *
	 * @param root the base directory; font variants are stored in subdirectories structured as
	 *             {@code <fontName>/<style>/<size>/<aa|raw>/}
	 */
	public FileSystemFontTextureDataCache(Path root) {
		this.root = root;
	}

	/**
	 * Uniquely identifies a rendered font variant by family, style, size, and anti-aliasing choice.
	 * Used as a cache key to locate the corresponding directory under the cache root.
	 *
	 * @param font      the AWT {@link Font} describing the family, style (bold/italic), and point size
	 * @param antiAlias {@code true} if the glyphs were rendered with anti-aliasing; the cache stores
	 *                  the two variants ({@code aa} and {@code raw}) in separate subdirectories
	 */
	public record FontLocator(Font font, boolean antiAlias) {
		static String style(Font font) {
			if (font.getStyle() == Font.PLAIN) {
				return "plain";
			}
			StringBuilder style = new StringBuilder();
			if (font.isBold()) {
				style.append("bold");
			}
			if (!style.isEmpty()) {
				style.append("_");
			}
			if (font.isItalic()) {
				style.append("italic");
			}
			return style.toString();
		}

		static int style(String style) {
			int result = Font.PLAIN;
			if (style == null || style.isEmpty() || style.equals("plain")) {
				return result;
			}
			if(style.contains("bold")) {
				result += Font.BOLD;
			}
			if(style.contains("italic")) {
				result += Font.ITALIC;
			}
			return result;
		}

		/**
		 * Derive the relative subdirectory path for this font variant.
		 *
		 * <p>Layout: {@code <fullFontName>/<style>/<pointSize>/<aa|raw>}.
		 * The path is relative to the cache root and can be resolved against it to get the
		 * absolute directory for this variant.</p>
		 *
		 * @return the relative cache path for this font variant
		 */
		public Path asPath() {
			return Path.of(font.getFontName(), style(font),Integer.toString(font.getSize()), antiAlias ? "aa" : "raw");
		}
	}

	/**
	 * Scan the cache root and return a stream of all font variants found on disk.
	 *
	 * <p>Traverses the three-level directory tree ({@code name/style/size/alias}) and constructs
	 * a {@link FontLocator} for each leaf directory. Directories that do not conform to the
	 * expected structure are silently skipped.</p>
	 *
	 * @return a stream of all discoverable font variants; empty if the root is missing or unreadable
	 */
	public Stream<FontLocator> load() {
		try(Stream<Path> fontNames = Files.list(root)) {
			// Stream of font folders (e.g., Arial, Courier)
			return fontNames.filter(Files::isDirectory)
							.flatMap(FileSystemFontTextureDataCache::handleFontNameFolder);
		}
		catch(IOException ioe) {
			LOG.error("Failed to list font names", ioe);
		}
		return Stream.empty();
	}

	private static Stream<FontLocator> handleFontNameFolder(Path fontFolder) {
		try(Stream<Path> fontStyles = Files.list(fontFolder)) {
			// Stream of size folders within each font folder
			final String fontName = fontFolder.getFileName().toString();
			return fontStyles
							.filter(Files::isDirectory)
							.flatMap(styleFolder -> handleFontStyleFolder(styleFolder, fontName));
		} catch (IOException e) {
			// Handle potential IOExceptions when listing sub-folders
			LOG.error("Failed to list font files", e);
			return Stream.empty();
		}
	}

	private static Stream<FontLocator> handleFontStyleFolder(Path styleFolder, String fontName) {
		try(Stream<Path> sizeFolders = Files.list(styleFolder)) {
			// a stream of folders for raw and anti-aliased versions of the font
			// parse the style from this folder name
			final int style = FontLocator.style(styleFolder.getFileName().toString());
			return sizeFolders.filter(Files::isDirectory)
							.flatMap(sizePath -> handleFontSizeFolder(sizePath, fontName, style));
		} catch (NumberFormatException | IOException e) {
			// Ignore folders that are not valid font sizes
			LOG.error("Unable to use font size folder", e);
			return Stream.empty();
		}

	}

	private static Stream<FontLocator> handleFontSizeFolder(Path sizeFolder, final String fontName, final int fontStyle) {
		try(Stream<Path> aliasFolders = Files.list(sizeFolder)) {
			// a stream of folders for raw and anti-aliased versions of the font
			// parse the size from this folder name
			final int fontSize = Integer.parseInt(sizeFolder.getFileName().toString());
			return aliasFolders.filter(Files::isDirectory)
							.map(aliasPath -> {
								final boolean aliased = aliasPath.getFileName().toString().equals("aa");
								//noinspection MagicConstant - see FontLocator#style(String)
								return new FontLocator(new Font(fontName, fontStyle, fontSize), aliased);
							});
		} catch (NumberFormatException | IOException e) {
			// Ignore folders that are not valid font sizes
			LOG.error("Unable to use font size folder", e);
			return Stream.empty();
		}
	}

	/**
	 * Load the cached {@link FontTextureData} for a specific font variant, if it exists.
	 *
	 * @param fontLocator identifies the font variant to load
	 * @return the cached atlas data, or empty if no cache entry exists for this variant
	 */
	public Optional<FontTextureData> load(FontLocator fontLocator) {
		final var fontPath = root.resolve(fontLocator.asPath());
		if (Files.exists(fontPath)) {
			try {
				return Optional.of(loadFontTextureData(fontPath));
			}
			catch(IOException ioe) {
				LOG.error("Unable to load FontTextureData", ioe);
			}
		}
		return Optional.empty();
	}

	private FontTextureData loadFontTextureData(Path fontPath) throws IOException {
		return new FontTextureData(loadGlyphIMages(), loadGlyphData(), loadCombinedImage(fontPath));
	}

	private BufferedImage loadCombinedImage(Path fontPath) throws IOException {
		return ImageIO.read(Files.newInputStream(fontPath.resolve("combined.png")));
	}

	private Map<Character, GlyphData> loadGlyphData() throws IOException {
		throw new UnsupportedOperationException();
	}

	private Map<Character, GlyphImage> loadGlyphIMages()throws IOException  {
		throw new UnsupportedOperationException();
	}

	private GlyphImage loadGlyphImage(Path fontPath) throws IOException {
		throw new UnsupportedOperationException();
	}

	/**
	 * Persist the given font texture data to disk under the directory derived from {@code locator}.
	 *
	 * <p>If the target directory already exists the data is not overwritten; a warning is logged
	 * instead. This prevents accidental corruption of an existing cache entry.</p>
	 *
	 * @param locator         identifies the font variant being stored
	 * @param fontTextureData the atlas data to persist; contains the combined strip image,
	 *                        individual glyph images, and per-glyph metrics
	 * @throws IOException if directory creation or image/data I/O fails
	 */
	public void store(FontLocator locator, FontTextureData fontTextureData) throws IOException {
		final var fontPath = root.resolve(locator.asPath());
		if (!Files.exists(fontPath)) {
			Files.createDirectories(fontPath);
			// now we need to store the combined image
			storeCombinedImage(fontPath, fontTextureData.combined());
			storeGlyphImages(fontPath, fontTextureData.glyphImages());
			storeGlyphData(fontPath, fontTextureData.glyphData());
		}
		else {
			LOG.warn("Font data {} already exists, skipped", fontPath);
		}
	}

	private void storeGlyphData(Path fontPath, Map<Character, GlyphData> data) throws IOException {
		final Path combinedPath = fontPath.resolve("combined");
		Files.createDirectory(combinedPath);
		for(Map.Entry<Character, GlyphData> entry : data.entrySet()) {
			final String hexCharacterID = Integer.toHexString(Character.codePointAt(String.valueOf(entry.getKey()), 0));
			GlyphData glyphData = entry.getValue();
			Files.writeString(combinedPath.resolve(hexCharacterID+".data"),
							"bounds="+glyphData.bounds().x+","+glyphData.bounds().y+","+glyphData.bounds().width+","+glyphData.bounds().height +"\n" +
											"datum="+glyphData.datumOffset().x+","+glyphData.datumOffset().y,
							StandardCharsets.UTF_8, StandardOpenOption.CREATE);
		}
	}

	private void storeGlyphImages(Path fontPath, Map<Character, GlyphImage> data) throws IOException {
		final Path glyphsPath = fontPath.resolve("glyphs");
		Files.createDirectory(glyphsPath);
		final Path glyphImagesPath = glyphsPath.resolve("img");
		Files.createDirectory(glyphImagesPath);
		final Path glyphDataPath = glyphsPath.resolve("data");
		Files.createDirectory(glyphDataPath);
		for(Map.Entry<Character, GlyphImage> entry : data.entrySet()) {
			final String hexCharacterID = Integer.toHexString(Character.codePointAt(String.valueOf(entry.getKey()), 0));
			final GlyphImage glyphImage = entry.getValue();
			ImageIO.write(glyphImage.image(), "png", Files.newOutputStream(glyphImagesPath.resolve(hexCharacterID+".png")));
			Files.writeString(glyphDataPath.resolve(hexCharacterID+".data"),
								"bounds="+glyphImage.bounds().x+","+glyphImage.bounds().y+","+glyphImage.bounds().width+","+glyphImage.bounds().height +"\n" +
										 "datum="+glyphImage.datum().x+","+glyphImage.datum().y,
							StandardCharsets.UTF_8, StandardOpenOption.CREATE);
		}
	}

	private void storeCombinedImage(Path fontPath, BufferedImage combined) throws IOException {
		ImageIO.write(combined, "png", fontPath.resolve("combined.png").toFile());
	}
}
