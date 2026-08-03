package com.asteroid.duck.opengl.util.palette;

import com.asteroid.duck.opengl.util.AbstractPassthruRenderer;
import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import com.asteroid.duck.opengl.util.resources.shader.ShaderSource;
import com.asteroid.duck.opengl.util.resources.texture.io.TextureData;
import com.asteroid.duck.opengl.util.resources.texture.Texture;
import com.asteroid.duck.opengl.util.resources.texture.TextureUnit;
import org.joml.Vector2i;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Basically an indexed colour palette implementation. RGB output colour values are looked up using
 * the palette index.
 */
public class PaletteRenderer extends AbstractPassthruRenderer {
	private static final Logger LOG = LoggerFactory.getLogger(PaletteRenderer.class);

	//language=GLSL
	private static final String VERTEX_SHADER = """
			#version 330

			in vec2 screenPosition;
			in vec2 texturePosition;
			out vec2 texCoords;

			void main() {
			    // report out to open GL the screen position
			    gl_Position = vec4(screenPosition, 0.0, 1.0);
			    // pass tecture coords to fragment shader
			    texCoords = texturePosition;
			}
			""";

	//language=GLSL
	private static final String FRAGMENT_SHADER = """
			#version 460

			precision mediump float;

			uniform sampler2D tex;
			uniform sampler2D palette;
			uniform ivec2 palSize;

			in vec2 texCoords;
			out vec4 fragColor;

			void main() {
			    // lookup palette index for texel (the red channel, 0-1 with R16 precision)
			    float pos = texture(tex, texCoords).r;
			    // decode linear position into 2D palette coords
			    float pixelIndex = pos * float(palSize.x * palSize.y);
			    float col = (mod(pixelIndex, float(palSize.x)) + 0.5) / float(palSize.x);
			    float row = (floor(pixelIndex / float(palSize.x)) + 0.5) / float(palSize.y);
			    fragColor = clamp(texture(palette, vec2(col, row)), 0., 1.);
			}
			""";

	// indexed texture
	private final String textureName;

	// the palette texture with an RGB value for each index
	private Texture palette;
	private TextureUnit paletteUnit;
	private final String paletteName;

	/**
	 * Create a palette renderer for the named indexed texture using the default {@code "palette"} palette texture.
	 *
	 * @param name logical name of the indexed source texture in the resource manager
	 */
	public PaletteRenderer(String name) {
		this(name, "palette");
	}

	/**
	 * Create a palette renderer with explicit texture names.
	 *
	 * @param name        logical name of the indexed source texture
	 * @param paletteName logical name of the 1-D RGBA palette texture in the resource manager;
	 *                    each texel maps one palette index to an output colour
	 */
	private PaletteRenderer(String name, String paletteName) {
		this.textureName = name;
		this.paletteName = paletteName;
	}

	/**
	 * Create a 256-entry greyscale palette where index {@code i} maps to RGB({@code i},{@code i},{@code i}).
	 *
	 * @return a 256×1 RGBA {@link TextureData} suitable for upload as a palette texture
	 */
	public static TextureData greyScale() {
		ByteBuffer raw = BufferUtils.createByteBuffer(256 * 4);
		for (int i = 0; i < 256; i++) {
			raw.put((byte) i);
			raw.put((byte) i);
			raw.put((byte) i);
			raw.put((byte) 255); // A
		}
		raw.flip();
		return new TextureData(raw, new Dimension(256, 1));
	}

	/**
	 * Create a 256-entry test palette with a cycling RGB pattern: red sweeps 0–255, green wraps
	 * 128→255→0→127, and blue sweeps 255→0. Useful for verifying palette lookup is working correctly.
	 *
	 * @return a 256×1 RGBA {@link TextureData} suitable for upload as a palette texture
	 */
	public static TextureData rbgTestScale() {
		ByteBuffer raw = BufferUtils.createByteBuffer(256 * 4);
		int g = 128;
		int b = 255;
		for (int i = 0; i < 256; i++) {
			raw.put((byte) i); //r 0 - 255
			raw.put((byte) g); //g 128 - 255 / 0 - 127
			raw.put((byte) b); //b 255 - 0
			g += 1;
			if (g > 255) g = 0;
			b -= 1;
			raw.put((byte) 255); // A
		}
		raw.flip();
		return new TextureData(raw, new Dimension(256, 1));
	}

	/**
	 * CLI entry point: renders the {@link #rbgTestScale()} palette to a PNG file named {@code test.png}
	 * in the current working directory. Useful for visually verifying the palette colours.
	 *
	 * @param args command-line arguments (ignored)
	 * @throws IOException if the PNG file cannot be written
	 */
	public static void main(String[] args) throws IOException {
		BufferedImage image = new BufferedImage(256, 1, BufferedImage.TYPE_INT_ARGB);
		TextureData data = rbgTestScale();
		// Iterate over the byte array, 4 bytes at a time
		ByteBuffer raw = data.buffer();
		IntBuffer intBuffer = raw.asIntBuffer();
		for (int i = 0; i < 256; i ++) {
			// Convert the RGBA bytes to an integer
			int pixel = intBuffer.get(i);

			// Set the pixel in the image
			image.setRGB(i / 4, 0, pixel);
		}
		var file = new java.io.File("test.png");
		ImageIO.write(image, "png", file);
		LOG.info("Wrote palette test image: {}", file.getAbsolutePath());
	}


	protected ShaderProgram initShaderProgram(RenderContext ctx) throws IOException {
		return ShaderProgram.compile(
				ShaderSource.fromClass(VERTEX_SHADER, PaletteRenderer.class),
				ShaderSource.fromClass(FRAGMENT_SHADER, PaletteRenderer.class),
				null);
	}

	protected Texture initTexture(RenderContext ctx) {

		this.palette = ctx.getResourceManager().getTexture(paletteName);
		this.paletteUnit = ctx.getResourceManager().nextTextureUnit();
		this.paletteUnit.bind(palette);

		return ctx.getResourceManager().getTexture(textureName);
	}

	@Override
	public void init(RenderContext ctx) throws IOException {
		super.init(ctx);
		shaderProgram.use(ctx);
		this.paletteUnit.useInShader(shaderProgram, "palette");
		shaderProgram.uniforms().get("palSize", Vector2i.class)
				.set(new Vector2i(palette.getWidth(), palette.getHeight()));
	}

	@Override
	public void dispose() {
		paletteUnit.dispose();
		palette.dispose();
		super.dispose();
	}
}
