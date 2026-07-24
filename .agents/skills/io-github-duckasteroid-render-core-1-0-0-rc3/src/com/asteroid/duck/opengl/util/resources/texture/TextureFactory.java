package com.asteroid.duck.opengl.util.resources.texture;

import com.asteroid.duck.opengl.util.resources.io.Loader;
import com.asteroid.duck.opengl.util.resources.texture.io.TextureData;
import com.asteroid.duck.opengl.util.resources.texture.io.ImageLoadingOptions;
import com.asteroid.duck.opengl.util.resources.texture.io.JavaImageLoader;
import com.asteroid.duck.opengl.util.resources.texture.io.RawLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;

public class TextureFactory {
	private static final Logger log = LoggerFactory.getLogger(TextureFactory.class);
	private static final Set<String> IMAGE_FORMATS = Set.of("png", "jpg", "jpeg");

	interface FormatHelper {
        /**
         * Create a blank {@link BufferedImage} of the appropriate raster type for this format.
         *
         * @param dimension pixel dimensions of the image to create
         * @return a new, transparent (if applicable) image in this format's raster type
         */
		BufferedImage apply(Dimension dimension);
		int internalFormat();
		int imageFormat();

		int dataType();

		void verify(TextureData data) throws IllegalArgumentException;
		ByteBuffer pixelData(BufferedImage bufferedImage);
	}

	private final Loader loader;

	public TextureFactory(Loader root) {
		this.loader = root;
	}

	public static Texture createTexture(Rectangle screen, boolean is32f) {
		TextureOptions options = null;
		if (is32f) {
			options = new TextureOptions(DataFormat.GRAY, Filter.LINEAR, Wrap.REPEAT);
		}
		else {
			options = new TextureOptions(DataFormat.RGBA, Filter.LINEAR, Wrap.REPEAT);
		}
		return createTexture(screen, null, options );
	}

	public static Texture createTexture(Rectangle screen, Dimension pad, TextureOptions options) {
		if (pad == null) {
			pad = new Dimension(0, 0);
		}
		Texture offscreen = new Texture();
		if (options != null) {
			offscreen.setFilter(options.filter());
			offscreen.setWrap(options.wrap());
			offscreen.setInternalFormat(options.dataFormat().internalFormat());
			offscreen.setImageFormat(options.dataFormat().imageFormat());
			offscreen.setDataType(options.dataFormat().dataType());
		}
		offscreen.generate(screen.width + pad.width, screen.height + pad.height, 0);
		return offscreen;
	}


	public static Texture createTexture(ImageLoadingOptions options, TextureData data) {
		Texture tex = new Texture();

		tex.setInternalFormat( options.dataFormat().internalFormat());
		tex.setImageFormat( options.dataFormat().imageFormat());
		tex.setDataType( options.dataFormat().dataType());

		options.dataFormat().verify(data);

		tex.generate(data.size().width, data.size().height, data.buffer());
		return tex;
	}

	public Texture LoadTexture(String texturePath, ImageLoadingOptions options) throws IOException {
			TextureData textureData = loadTextureData(texturePath, options);
			return createTexture(options, textureData);
	}

	public TextureData loadTextureData(String texturePath, ImageLoadingOptions options) throws IOException {
		if (IMAGE_FORMATS.stream().anyMatch(texturePath::endsWith)) {
			if (log.isTraceEnabled()) {
				log.trace("Loading image from {} using JavaImageLoader", texturePath);
			}
			return new JavaImageLoader(loader).load(texturePath, options);
		}
		if (log.isTraceEnabled()) {
			log.trace("Loading image from {} using RawImageLoader", texturePath);
		}
		return new RawLoader(extractDimensions(texturePath), loader).load(texturePath, options);
	}

	private Dimension extractDimensions(String texturePath) {
		String[] split = texturePath.split("\\.");
		if (split.length == 3) {
			final String dimString = split[1];
			String[] dim = dimString.split("x");
			return new Dimension(Integer.parseInt(dim[0]), Integer.parseInt(dim[1]));
		}
		throw new IllegalArgumentException("No dimensions component to file name (expected {name}.{width}x{height}.{filetype}");
	}
}
