package com.asteroid.duck.opengl.util;

import com.asteroid.duck.opengl.util.geom.Triangles;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import com.asteroid.duck.opengl.util.resources.shader.ShaderSource;
import com.asteroid.duck.opengl.util.resources.shader.vars.ShaderVariable;
import com.asteroid.duck.opengl.util.resources.shader.vars.ShaderVariables;
import com.asteroid.duck.opengl.util.resources.texture.Texture;
import com.asteroid.duck.opengl.util.resources.texture.TextureUnit;
import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * A renderer that translates a texture using a translation table (a special texture).
 * A translation table is a 2D texture where each pixel's value indicates the coordinates
 * in the source texture to sample from.
 */
public class TranslateTextureRenderer extends AbstractPassthruRenderer {
	private static final Logger LOG = LoggerFactory.getLogger(TranslateTextureRenderer.class);

	private static final String VERTEX_SHADER = """
			#version 330
			in vec2 texturePosition;
			in vec2 screenPosition;
			out vec2 texCoords;

			void main() {
			    // report out to open GL the
			    gl_Position = vec4(screenPosition, 0.0, 1.0);;
			    // pass coords to vertex shader
			    texCoords = texturePosition;
			}
			""";

	private static final String FRAGMENT_SHADER = """
			#version 460

			precision mediump float;

			uniform sampler2D tex;
			uniform usampler2D map;
			uniform vec2 dimensions;

			in vec2 texCoords;
			out vec4 fragColor;

			void main() {
			    // find the source texel coordinates for the current texel from the map
			    uvec2 mappedCoords = texture(map, texCoords).xy; // point x & y
			    // convert the texel coordinates to normalised form
			    vec2 normalizedCoords = vec2(mappedCoords) / dimensions;
			    // get the color from the source texture at that location
			    fragColor = texture(tex, normalizedCoords);
			}
			""";

	private final String textureName;

	private final String translationTableTextureName;
	private TextureUnit translationTableTextureUnit;

	private final ShaderVariables variables = new ShaderVariables();

	public TranslateTextureRenderer(String textureName, String translationTableTextureName) {
		this.textureName = textureName;
		this.translationTableTextureName = translationTableTextureName;
	}

	protected ShaderProgram initShaderProgram(RenderContext ctx) throws IOException {
		return ShaderProgram.compile(
				ShaderSource.fromClass(VERTEX_SHADER, TranslateTextureRenderer.class),
				ShaderSource.fromClass(FRAGMENT_SHADER, TranslateTextureRenderer.class),
				null);
	}

	protected Texture initTexture(RenderContext ctx) {
		shaderProgram.use(ctx);

		// setup the map texture so we can refer to it
		Texture translationTableTexture = ctx.getResourceManager().getTexture(translationTableTextureName);
		this.translationTableTextureUnit = ctx.getResourceManager().nextTextureUnit();
		translationTableTextureUnit.bind(translationTableTexture);
		translationTableTextureUnit.useInShader(shaderProgram, "map");

		// tell the shader our dimensions
		variables.add(ShaderVariable.vec2fVariable("dimensions", this::dimensions));

		// setup the source texture so we can refer to it
		return ctx.getResourceManager().getTexture(textureName);
	}

	private Vector2f dimensions() {
		return new Vector2f(texture.getWidth(), texture.getHeight());
	}

	@Override
	public void doRenderWithShader(RenderContext ctx) {
		variables.updateForRender(ctx, shaderProgram);
		super.doRenderWithShader(ctx);
	}

	@Override
	public void dispose() {
		translationTableTextureUnit.dispose();
		super.dispose();
	}
}
