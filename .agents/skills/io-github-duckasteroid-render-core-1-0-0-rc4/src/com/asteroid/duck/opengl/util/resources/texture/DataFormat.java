package com.asteroid.duck.opengl.util.resources.texture;

import com.asteroid.duck.opengl.util.resources.texture.io.TextureData;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Hashtable;

import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.opengl.GL30.*;

/**
 * High levels formats of Texture, used during the texture loading process.
 * Defines the number of channels and the data type of those channels.
 * e.g. RGBA etc.
 */
public enum DataFormat implements TextureFactory.FormatHelper {
	/** Red, Green, Blue &amp; Alpha - 4 bytes per pixel */
	RGBA(GL_RGBA, GL_RGBA8, GL_UNSIGNED_BYTE, 4) {
		public BufferedImage apply(Dimension d) {
			var glAlphaColorModel = new ComponentColorModel(ColorSpace
							.getInstance(ColorSpace.CS_sRGB), new int[] { 8, 8, 8, 8 },
							true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);
			var raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
							d.width, d.height, 4, null);;
			return new BufferedImage(glAlphaColorModel, raster, true,
							new Hashtable<>());
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
			for (int y = 0; y < img.getHeight(); y++) {
				for (int x = 0; x < img.getWidth(); x++) {
					buffer.putFloat(luminance(new Color(img.getRGB(x, y))));
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
			for (int y = 0; y < img.getHeight(); y++) {
				for (int x = 0; x < img.getWidth(); x++) {
					// scale luminance 0.0-1.0 → 0-65535 for GL_R16 unsigned normalized
					buffer.putShort((short)(luminance(new Color(img.getRGB(x, y))) * 65535f));
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

	@Override
	public ByteBuffer pixelData(BufferedImage img) {
		// build a byte buffer from the image data
		// that OpenGL can use to produce a texture.
		byte[] data = ((DataBufferByte) img.getRaster().getDataBuffer())
						.getData();

		ByteBuffer imageBuffer = ByteBuffer.allocateDirect(data.length);
		imageBuffer.order(ByteOrder.nativeOrder());
		imageBuffer.put(data, 0, data.length);
		imageBuffer.flip();
		return imageBuffer;
	}

	static float luminance(Color color) {
		return 0.2126f * (color.getRed() / 255f)
			+ 0.7152f * (color.getGreen() / 255f)
			+ 0.0722f * (color.getBlue() / 255f);
	}
}
