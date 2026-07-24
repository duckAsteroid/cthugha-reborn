package com.asteroid.duck.opengl.util.resources.texture;

import com.asteroid.duck.opengl.util.resources.Resource;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import com.asteroid.duck.opengl.util.resources.texture.Texture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

import static org.lwjgl.opengl.GL11.glGetIntegerv;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS;

/**
 * Texture units are the slots textures are loaded into that can be bound into shaders.
 * Shaders refer to textures (typically in a {@link com.asteroid.duck.opengl.util.resources.shader.Uniform})
 * by the index of the texture unit the texture is bound to.
 * <p>Clients can change the bound texture at any time</p>
 */
public class TextureUnit implements Resource, Comparable<TextureUnit> {
	private static final Logger LOG = LoggerFactory.getLogger(TextureUnit.class);
	private static final int MAX = maxTextureUnits();

    private static int maxTextureUnits() {
		try {
			int[] maxUnits = new int[1];
			glGetIntegerv(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS, maxUnits);
			var max = maxUnits[0];
			if (max > 0) {
				LOG.trace("Found max texture units [GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS]: {}", max);
				return max;
			}
		}
		catch (Exception e) {
			LOG.error("Unable to read GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS", e);
		}
		return 15; // default
    }

	/**
	 * The index of the slot (0 is the first)
	 */
	private final int index;
	/**
	 * The GL constant for this slot
	 * @see org.lwjgl.opengl.GL13#GL_TEXTURE0
	 */
	private final int glTextureUnitID;
	/**
	 * The texture (if any) bound to this texture unit (slot)
	 */
	private Texture boundTexture;
	/**
	 * This function (callback) is called when the TextureUnit is disposed of.
	 * Like a lightweight (one) listener for disposal events.
	 */
	private final Consumer<TextureUnit> disposalFunction;

	private TextureUnit(int index, int glTextureUnitID, Consumer<TextureUnit> disposalFunction) {
		this.index = index;
		this.glTextureUnitID = glTextureUnitID;
		this.disposalFunction = disposalFunction;
	}

	public static TextureUnit index(int index) {
		return index(index, null);
	}

	public static TextureUnit index(int index, Consumer<TextureUnit> disposalFunction) {
		if (index < 0 || index > MAX) throw new IllegalArgumentException("Texture unit index must be between 0 and 31");
		return new TextureUnit(index, GL_TEXTURE0 + index, disposalFunction);
	}

	public void activate() {
		glActiveTexture(glTextureUnitID);
		if (LOG.isTraceEnabled()) {
			LOG.trace("Activated texture unit: {}", this);
		}
	}

	public void bind(Texture texture) {
		activate();
		texture.bind();
		boundTexture = texture;
		if (LOG.isTraceEnabled()) {
			LOG.trace("Bound texture {} to unit: {}", texture, this);
		}
	}

	public int getIndex() {
		return index;
	}

	public Texture getBoundTexture() {
		return boundTexture;
	}

	public void useInShader(ShaderProgram program, String variable) {
		if (LOG.isTraceEnabled()) {
			LOG.trace("Using {} in shader {} via uniform '{}'={}", this, program.shortDebugName(), variable, index);
		}
		program.uniforms().get(variable, Integer.class).set(index);
	}

	@Override
	public void dispose() {
		if (disposalFunction != null) {
			disposalFunction.accept(this);
		}
	}

	@Override
	public int compareTo(TextureUnit other) {
		return this.index - other.index;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("TextureUnit");
		sb.append("(id=").append(index).append(",GL_TEXTURE").append(glTextureUnitID - GL_TEXTURE0).append("):");
		sb.append(" boundTexture=").append(boundTexture);
		return sb.toString();
	}
}
