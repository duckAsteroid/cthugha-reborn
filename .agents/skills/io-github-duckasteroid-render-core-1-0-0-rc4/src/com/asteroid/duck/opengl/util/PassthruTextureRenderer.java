package com.asteroid.duck.opengl.util;

import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import com.asteroid.duck.opengl.util.resources.texture.Texture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Takes a texture and renders it using a fullscreen quad (two triangles)
 */
public class PassthruTextureRenderer extends AbstractPassthruRenderer implements RenderedItem {

	private static final Logger LOG = LoggerFactory.getLogger(PassthruTextureRenderer.class);

	private final String shaderName;
	/** Logical name of the source texture looked up from the resource manager each frame. */
	protected final String textureName;

	/**
	 * Create a passthrough renderer that uses the default {@code "passthru"} shader.
	 *
	 * @param textureName logical name of the texture to display (must be registered before {@link #init})
	 */
	public PassthruTextureRenderer(String textureName) {
		this(textureName, "passthru");
	}

	/**
	 * Create a passthrough renderer with a custom shader.
	 *
	 * @param textureName logical name of the texture to display
	 * @param shaderName  logical name of the shader program to load via the simple-shader loader
	 */
	public PassthruTextureRenderer(String textureName, String shaderName){
		this.textureName = textureName;
		this.shaderName = shaderName;
	}

	protected ShaderProgram initShaderProgram(RenderContext ctx) throws IOException {
		return ctx.getResourceManager().getShaderLoader().LoadSimpleShaderProgram(shaderName);
	}

	@Override
	protected Texture initTexture(RenderContext ctx) {
		return ctx.getResourceManager().getTexture(textureName);
	}

}
