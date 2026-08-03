package com.asteroid.duck.opengl.util.palette;

import com.asteroid.duck.opengl.util.resources.manager.ResourceManager;
import com.asteroid.duck.opengl.util.resources.texture.io.ImageLoadingOptions;
import com.asteroid.duck.opengl.util.resources.texture.Texture;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Loads an image file as a 1-row palette texture and registers it with the
 * {@link ResourceManager} so it can be referenced by name in {@link PaletteRenderer}.
 *
 * <p>The image is cropped to a single horizontal row (via {@link ImageLoadingOptions#withSingleLine()})
 * regardless of its original height. The width of that row determines the number of palette
 * entries. The palette is registered under the image file's base name (no directory or extension).
 * Use {@link #size()} to query the entry count when you need to scale UV coordinates.</p>
 */
public class ColorPalette {
	private final Texture palette;
	private final String name;

	/**
	 * Load the given image as a palette texture and register it with the resource manager.
	 *
	 * @param mgr  the resource manager that will own and eventually dispose the palette texture
	 * @param file path to the palette image; the file's base name becomes the texture key in
	 *             the resource manager
	 * @throws IOException if the image file cannot be read or decoded
	 */
	public ColorPalette(ResourceManager mgr, String file) throws IOException {
		palette = mgr.getTextureFactory().LoadTexture(file, ImageLoadingOptions.DEFAULT.withSingleLine());
		// TODO figure out a name
		this.name = Path.of(file).getFileName().toString();
		mgr.putTexture(name, palette);
	}

	/**
	 * Returns the number of colour entries in this palette, equal to the pixel width of the
	 * loaded image row.
	 *
	 * @return palette entry count; use this to convert a linear index to a UV coordinate
	 */
	public int size() {
		return palette.getWidth();
	}
}
