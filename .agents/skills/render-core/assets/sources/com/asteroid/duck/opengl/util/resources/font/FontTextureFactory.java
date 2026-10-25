package com.asteroid.duck.opengl.util.resources.font;

import com.asteroid.duck.opengl.util.resources.font.factory.FontTextureData;
import com.asteroid.duck.opengl.util.resources.font.factory.FontTextureDataFactory;
import com.asteroid.duck.opengl.util.resources.texture.DataFormat;
import com.asteroid.duck.opengl.util.resources.texture.io.TextureData;
import com.asteroid.duck.opengl.util.resources.texture.TextureFactory;
import com.asteroid.duck.opengl.util.resources.texture.io.ImageLoadingOptions;

import java.awt.*;
import java.nio.ByteBuffer;

/**
 * Two-step font-atlas builder: uses AWT to rasterise glyphs, then uploads the result as an
 * OpenGL {@link FontTexture}.
 *
 * <p>Internally delegates glyph rasterisation to {@link FontTextureDataFactory}, which packs
 * all printable characters into a single horizontal image strip. {@link #createFontTexture()}
 * then converts that strip to a GPU texture and returns the finished {@link FontTexture} ready
 * for use in text rendering.</p>
 */
public class FontTextureFactory {
	private final FontTextureDataFactory fontTextureDataFactory;

	/**
	 * Create a factory for the specified AWT font and anti-aliasing preference.
	 *
	 * @param font       the AWT font to rasterise; controls family, style, and point size
	 * @param antiAlias  {@code true} to render glyphs with anti-aliasing
	 */
	public FontTextureFactory(final Font font, boolean antiAlias) {
		this.fontTextureDataFactory = new FontTextureDataFactory(font, antiAlias);
	}

	/**
	 * Rasterise all printable glyphs into a combined strip image, upload it as an RGBA GL texture,
	 * and return the finished font texture.
	 *
	 * <p>This method is the typical entry point: call it once at startup, keep the returned
	 * {@link FontTexture} for the lifetime of the GL context, and call {@link FontTexture#dispose()}
	 * during shutdown to free the GPU texture.</p>
	 *
	 * @return a ready-to-use {@link FontTexture} backed by a freshly allocated GL texture
	 */
	public FontTexture createFontTexture() {
		FontTextureData fontTextureData = fontTextureDataFactory.createFontTextureData();
		ByteBuffer rawBuffer = DataFormat.RGBA.pixelData(fontTextureData.combined());
		TextureData data = new TextureData(rawBuffer, fontTextureData.combinedSize());
		return new FontTexture(fontTextureData.glyphData(), TextureFactory.createTexture(ImageLoadingOptions.DEFAULT, data));
	}
}
