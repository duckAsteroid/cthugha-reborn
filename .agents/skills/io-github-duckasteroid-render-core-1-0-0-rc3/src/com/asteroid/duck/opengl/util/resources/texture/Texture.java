package com.asteroid.duck.opengl.util.resources.texture;

import com.asteroid.duck.opengl.util.resources.Resource;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.stream.Stream;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

/**
 * Represents an OpenGL Texture object. Typically, this is a pixel-based image in the graphics memory.
 */
public class Texture implements Resource {
	private static final Logger LOG = LoggerFactory.getLogger(Texture.class);

	// e.g. 2D or 1D
	Dimensions dimensions;

	private final int id;
	private int width;
	private int height;
	private int internalFormat;
	private int imageFormat;
	private int dataType;
	private Wrap wrap;
	private Filter filter;

	public Stream<OpenGLCoded> openGlCodedStream() {
		return Stream.of(wrap, filter);
	}

	/**
	 * Constructs a new Texture object.
	 *
	 * Initializes the texture with default values:
	 * - Width: 0
	 * - Height: 0
	 * - Internal Format: GL_RGB
	 * - Image Format: GL_RGB
	 * - Wrap: repeating
	 * - Filter: Linear interpolation
	 * - Type: 2D
	 * - Data Type: GL_UNSIGNED_BYTE
	 *
	 * Generates a unique texture ID using OpenGL's glGenTextures function.
	 * Logs the creation of the texture with the generated ID.
	 */
	public Texture() {
		this.width = 0;
		this.height = 0;
		this.internalFormat = GL_RGB;
		this.imageFormat = GL_RGB;
		this.wrap = Wrap.REPEAT;
		this.filter = Filter.LINEAR;
		this.dimensions = Dimensions.TWO_DIMENSION;
		this.dataType = GL_UNSIGNED_BYTE;
		this.id = glGenTextures();
		LOG.info("Created texture ID: {}", id);
	}

	/**
	 * The width and height dimensions as a 2d vector
	 */
	public Vector2f getDimensions() {
		return new Vector2f(width, height);
	}

	/**
	 * A matrix that can be used to normalise texture pixel coordinates
	 */
	public Matrix3x2f getNormalisationMatrix() {
		return new Matrix3x2f(
						1f / width, 0f,
						0f, -1f / height,
						0f, 1f
		);
	}

	// generate an empty texture (e.g. for rendering)
	public void generate(int width, int height, long pixels) {
		this.width = width;
		this.height = height;
		if (dimensions != Dimensions.TWO_DIMENSION) throw new IllegalArgumentException("Texture type must be 2D");
		// create texture
		glActiveTexture(GL_TEXTURE0); // unit 0 is the unmanaged scratch unit — never allocated by ResourceManager
		bind();
		glTexImage2D(dimensions.openGlCode(), 0, this.internalFormat, width, height, 0, this.imageFormat, this.dataType, pixels);
		// set Texture wrap and filter modes
		wrap.openGlParamsStream().forEach(param -> glTexParameteri(dimensions.openGlCode(), param, wrap.openGlCode()));
		filter.openGlParamsStream().forEach(param -> glTexParameteri(dimensions.openGlCode(), param, filter.openGlCode()));

		// unbind texture
		unbind();
	}

	public void generate(int width, int height, ByteBuffer data) {
		this.width = width;
		this.height = height;
		if (dimensions != Dimensions.TWO_DIMENSION) throw new IllegalArgumentException("Texture type must be 2D");
		// create Texture
		glActiveTexture(GL_TEXTURE0); // unit 0 is the unmanaged scratch unit — never allocated by ResourceManager
		bind();
		glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
		glTexImage2D(dimensions.openGlCode(), 0, this.internalFormat, width, height, 0, this.imageFormat, this.dataType, data);
		// set Texture wrap and filter modes
		wrap.openGlParamsStream().forEach(param -> glTexParameteri(dimensions.openGlCode(), param, wrap.openGlCode()));
		filter.openGlParamsStream().forEach(param -> glTexParameteri(dimensions.openGlCode(), param, filter.openGlCode()));
		// unbind texture
		unbind();
	}

	public void generate1D(int length, ByteBuffer data) {
		this.width = length;
		this.height = 1;
		this.dimensions = Dimensions.ONE_DIMENSION;
		this.wrap = Wrap.REPEAT;
		// create Texture
		glActiveTexture(GL_TEXTURE0); // unit 0 is the unmanaged scratch unit — never allocated by ResourceManager
		bind();
		glTexImage1D(this.dimensions.openGlCode(), 0, this.internalFormat, width, 0, this.imageFormat, this.dataType, data);
		// set Texture wrap and filter modes
		wrap.openGlParamsStream().forEach(param -> glTexParameteri(dimensions.openGlCode(), param, wrap.openGlCode()));
		filter.openGlParamsStream().forEach(param -> glTexParameteri(dimensions.openGlCode(), param, filter.openGlCode()));
		// unbind texture
		unbind();
	}

	public int getId() {
		return id;
	}

	void bind() {
		glBindTexture(dimensions.openGlCode(), id);
	}

	void unbind() {
		glBindTexture(dimensions.openGlCode(), 0);
	}

	public void dispose() {
		glDeleteTextures(id);
	}

	public void setInternalFormat(int fmt) {
		this.internalFormat = fmt;
	}

	public void setImageFormat(int fmt) {
		this.imageFormat = fmt;
	}

	public void setDataType(int fmt) {
		this.dataType = fmt;
	}

	public void setWrap(Wrap wrap) {
		this.wrap = wrap;
	}

	public void setFilter(Filter filter) {
		this.filter = filter;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Texture ID: ").append(id).append("; w=").append(width).append("; h=").append(height);
		return sb.toString();
	}

}
