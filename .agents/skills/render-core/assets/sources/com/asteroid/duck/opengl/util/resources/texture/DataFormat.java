package com.asteroid.duck.opengl.util.resources.texture;

import com.asteroid.duck.opengl.util.resources.texture.io.TextureData;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.opengl.GL30.*;

/**
 * High levels formats of Texture, used during the texture loading process.
 * Defines the number of channels and the data type of those channels.
 * e.g. RGBA etc.
 */
public enum DataFormat implements TextureFactory.FormatHelper {
	/**
	 * Red, Green, Blue &amp; Alpha - 4 bytes per pixel.
	 * <p>The staging image is {@link BufferedImage#TYPE_INT_ARGB}, which Java2D has fast loops to
	 * and from; {@link #pixelData} swizzles it to RGBA bytes in bulk.</p>
	 */
	RGBA(GL_RGBA, GL_RGBA8, GL_UNSIGNED_BYTE, 4) {
		public BufferedImage apply(Dimension d) {
			return new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_ARGB);
		}
	},
	/** 1 channel greyscale using 32bit float per pixel */
	GRAY(GL_RED, GL_R32F, GL_FLOAT, 4) {
		public BufferedImage apply(Dimension d) {
			return new BufferedImage(d.width, d.height, BufferedImage.TYPE_BYTE_GRAY);
		}

		public ByteBuffer pixelData(BufferedImage img) {
			ByteBuffer buffer = ByteBuffer.allocateDirect(img.getWidth() * img.getHeight() * 4);
			buffer.order(ByteOrder.nativeOrder());
			int[] row = new int[img.getWidth()];
			for (int y = 0; y < img.getHeight(); y++) {
				argbRow(img, y, row);
				for (int argb : row) {
					buffer.putFloat(luminance(argb));
				}
			}
			buffer.flip();
			return buffer;
		}
	},
	/** 1 channel greyscale using 16-bit normalized fixed point per pixel (65536 palette entries) */
	GRAY_16(GL_RED, GL_R16, GL_UNSIGNED_SHORT, 2) {
		public BufferedImage apply(Dimension d) {
			return new BufferedImage(d.width, d.height, BufferedImage.TYPE_BYTE_GRAY);
		}

		public ByteBuffer pixelData(BufferedImage img) {
			ByteBuffer buffer = ByteBuffer.allocateDirect(img.getWidth() * img.getHeight() * 2);
			buffer.order(ByteOrder.nativeOrder());
			int[] row = new int[img.getWidth()];
			for (int y = 0; y < img.getHeight(); y++) {
				argbRow(img, y, row);
				for (int argb : row) {
					// scale luminance 0.0-1.0 → 0-65535 for GL_R16 unsigned normalized
					buffer.putShort((short)(luminance(argb) * 65535f));
				}
			}
			buffer.flip();
			return buffer;
		}
	},
	/** 2 channel RED/GREEN using 2 x 16 bit unsigned ints per pixel */
	TWO_CHANNEL_16_BIT(GL_RG_INTEGER, GL_RG16UI, GL_UNSIGNED_SHORT, 4) {
		public BufferedImage apply(Dimension d) {
			throw new UnsupportedOperationException("No image for RG16UI");
		}
	};

	private final int imageFormat;
	private final int internalFormat;
	private final int bytesPerPixel;
	private final int dataType;

	DataFormat(int imageFormat, int internalFormat, int dataType, int bytesPerPixel) {
		this.imageFormat = imageFormat;
		this.internalFormat = internalFormat;
		this.bytesPerPixel = bytesPerPixel;
		this.dataType = dataType;
	}

	@Override
	public int imageFormat() {
		return imageFormat;
	}

	@Override
	public int internalFormat() {
		return internalFormat;
	}

	@Override
	public int dataType() {
		return dataType;
	}

	@Override
	public void verify(TextureData data) throws IllegalArgumentException {
		int expectedSize = data.totalPixelCount() * bytesPerPixel;
		if (expectedSize != data.buffer().remaining()) {
			throw new IllegalArgumentException("Buffer size was "+ data.buffer().remaining() + ", expected " +expectedSize);
		}
	}

	/**
	 * Converts any image to tightly packed RGBA bytes (one byte per channel, rows top to bottom).
	 * Fastest for {@link BufferedImage#TYPE_INT_ARGB} images, as produced by {@link #RGBA}'s
	 * {@link #apply}.
	 */
	@Override
	public ByteBuffer pixelData(BufferedImage img) {
		int width = img.getWidth();
		// big-endian so each putInt lays the bytes down as R, G, B, A
		ByteBuffer imageBuffer = ByteBuffer.allocateDirect(width * img.getHeight() * 4).order(ByteOrder.BIG_ENDIAN);
		int[] row = new int[width];
		for (int y = 0; y < img.getHeight(); y++) {
			argbRow(img, y, row);
			for (int argb : row) {
				imageBuffer.putInt((argb << 8) | (argb >>> 24));
			}
		}
		imageBuffer.flip();
		return imageBuffer.order(ByteOrder.nativeOrder());
	}

	/**
	 * Reads one row of {@code img} as packed, non-premultiplied sRGB ARGB ints into {@code row},
	 * in bulk rather than through a per-pixel {@code getRGB(x, y)}.
	 */
	static void argbRow(BufferedImage img, int y, int[] row) {
		if (img.getType() == BufferedImage.TYPE_INT_ARGB) {
			img.getRaster().getDataElements(0, y, img.getWidth(), 1, row); // already ARGB: straight copy
		} else {
			img.getRGB(0, y, img.getWidth(), 1, row, 0, img.getWidth());
		}
	}

	static float luminance(int argb) {
		return 0.2126f * (((argb >> 16) & 0xFF) / 255f)
			+ 0.7152f * (((argb >> 8) & 0xFF) / 255f)
			+ 0.0722f * ((argb & 0xFF) / 255f);
	}
}
