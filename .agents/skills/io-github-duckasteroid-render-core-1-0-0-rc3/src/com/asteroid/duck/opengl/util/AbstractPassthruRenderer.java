package com.asteroid.duck.opengl.util;

import com.asteroid.duck.opengl.util.geom.Rectangle;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import com.asteroid.duck.opengl.util.resources.texture.Texture;
import com.asteroid.duck.opengl.util.resources.texture.TextureUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.lwjgl.opengl.GL11C.glDrawElements;

/**
 * Base class for rendered items that display a texture on a full-screen quad (two triangles).
 *
 * <p>Subclasses implement the two abstract factory methods to supply a {@link Texture} and a
 * compiled {@link ShaderProgram}. Everything else — binding the texture unit, setting up the
 * screen-filling {@link Rectangle} geometry, and issuing the draw call — is handled here.</p>
 *
 * <h2>Subclassing contract</h2>
 * <ol>
 *   <li>Override {@link #initTexture} to return the source texture (e.g. from the
 *       {@link com.asteroid.duck.opengl.util.resources.manager.ResourceManager}).</li>
 *   <li>Override {@link #initShaderProgram} to compile and return a {@link ShaderProgram}; this
 *       is also the right place to register additional {@link com.asteroid.duck.opengl.util.resources.shader.vars.ShaderVariable}
 *       bindings before the first render.</li>
 *   <li>Optionally override {@link #doRenderWithShader} to push per-frame uniforms before
 *       delegating to {@code super.doRenderWithShader(ctx)} for the actual draw call.</li>
 * </ol>
 */
public abstract class AbstractPassthruRenderer implements RenderedItem {

	private static final Logger LOG = LoggerFactory.getLogger(AbstractPassthruRenderer.class);

	/**
	 * The compiled shader program used to render the quad. Available after {@link #init} completes;
	 * subclasses may read it in {@link #doRenderWithShader} to set uniforms directly.
	 */
	protected ShaderProgram shaderProgram = null;

	/**
	 * The source texture bound to texture unit 0 under the {@code tex} sampler uniform.
	 * Available after {@link #init} completes.
	 */
	protected Texture texture;

	/** Default constructor for subclasses. No GL resources are allocated here. */
	protected AbstractPassthruRenderer() {}
	private TextureUnit textureUnit;
	private Rectangle renderedShape;

	@Override
	public void init(RenderContext ctx) throws IOException {
		this.shaderProgram = initShaderProgram(ctx);
		this.texture = initTexture(ctx);
		this.textureUnit = initTextureUnit(ctx);
		this.renderedShape = initBuffers(ctx);
	}

	/**
	 * Supply the texture to be displayed on the quad.
	 * Called during {@link #init}; must return a non-null {@link Texture} that is already
	 * registered with the {@link com.asteroid.duck.opengl.util.resources.manager.ResourceManager}.
	 *
	 * @param ctx the active render context
	 * @return the source texture; must not be {@code null}
	 */
	protected abstract Texture initTexture(RenderContext ctx);

	/**
	 * Compile and return the shader program used to render the quad.
	 * Called during {@link #init}, after the subclass has had a chance to register any
	 * {@link com.asteroid.duck.opengl.util.resources.shader.vars.ShaderVariable} bindings.
	 *
	 * @param ctx the active render context
	 * @return the compiled shader; must not be {@code null}
	 * @throws IOException if shader source cannot be read from the classpath
	 */
	protected abstract ShaderProgram initShaderProgram(RenderContext ctx) throws IOException;

	/**
	 * Allocate the next available texture unit, bind the texture to it, and wire the unit to the
	 * {@code tex} sampler uniform in the shader.
	 *
	 * @param ctx the active render context
	 * @return the allocated and configured texture unit
	 */
	protected TextureUnit initTextureUnit(RenderContext ctx) {
		shaderProgram.use(ctx);
		TextureUnit textureUnit = ctx.getResourceManager().nextTextureUnit();
		textureUnit.bind(texture);
		textureUnit.useInShader(shaderProgram, "tex");
		return textureUnit;
	}

	/**
	 * Build the full-screen quad geometry and bind it to the shader's vertex attribute locations.
	 * The quad uses two vertex attributes: {@code screenPosition} (clip-space XY) and
	 * {@code texturePosition} (UV coordinates in [0, 1]).
	 *
	 * @param ctx the active render context
	 * @return the initialised rectangle geometry ready for rendering
	 * @throws IOException if geometry resources cannot be loaded
	 */
	protected Rectangle initBuffers(RenderContext ctx) throws IOException {
		Rectangle renderedShape = new Rectangle(ctx, "screenPosition", "texturePosition");
		renderedShape.getVertexArrayObject().bind(ctx);
		renderedShape.getVertexBufferObject().setup(shaderProgram);
		if (LOG.isTraceEnabled()) {
            LOG.trace("Initialised: {}", renderedShape);
		}
		return renderedShape;
	}

	@Override
	public void doRender(RenderContext ctx) {
		shaderProgram.use(ctx);
		doRenderWithShader(ctx);
	}

	/**
	 * Perform the actual draw of the full-screen quad, assuming the shader is already bound.
	 * Subclasses may override this to push per-frame uniforms before calling
	 * {@code super.doRenderWithShader(ctx)}.
	 *
	 * @param ctx the active render context
	 */
	public void doRenderWithShader(RenderContext ctx) {
		renderedShape.render(ctx);
	}

	@Override
	public void dispose() {
		renderedShape.destroy();
		shaderProgram.dispose();
		textureUnit.dispose();
	}
}
