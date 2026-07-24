package com.asteroid.duck.opengl.util.resources.texture.io;

import com.asteroid.duck.opengl.util.resources.io.Loader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Uses Java ImageIO to load {@link TextureData} data from disk.
 * First, create a BufferedImage of the correct raster type.
 * Draw the image onto that raster image and then return the raw raster bytes.
 */
public class JavaImageLoader implements TextureDataLoader {
	private static final Logger log = LoggerFactory.getLogger(JavaImageLoader.class);
	private final Loader loader;
	/**
	 * Create a loader that reads image data via the given classpath/file-system abstraction.
	 *
	 * @param loader the resource loader used to open image streams by path
	 */
	public JavaImageLoader(Loader loader) {
		this.loader = loader;
	}
	@Override
	public TextureData load(String path, ImageLoadingOptions options) throws IOException {
		try(InputStream inputStream = loader.open(path)) {
			BufferedImage bufferedImage = ImageIO.read(inputStream);
			if (options.singleLine()) {
				bufferedImage = bufferedImage.getSubimage(0, 0, bufferedImage.getWidth(), 1);
			}

			var dims = new Dimension(bufferedImage.getWidth(), bufferedImage.getHeight());
			var texImage = options.dataFormat().apply(dims);

			// copy the source image into the produced image
			Graphics2D g = (Graphics2D) texImage.getGraphics();
			g.setColor(new Color(0f, 0f, 0f, 0f)); // transparent
			g.fillRect(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight());
			if (options.flipY()) {
				g.scale(1, -1);
				g.translate(0, -bufferedImage.getHeight());
			}
			g.drawImage(bufferedImage, 0, 0, null);


			ByteBuffer imageBuffer = options.dataFormat().pixelData(texImage);
			if (log.isTraceEnabled()) {
				log.trace("Loaded {} bytes of image data ({})", imageBuffer.limit(), options);
			}
			return new TextureData(imageBuffer, dims);
		}
	}
}
