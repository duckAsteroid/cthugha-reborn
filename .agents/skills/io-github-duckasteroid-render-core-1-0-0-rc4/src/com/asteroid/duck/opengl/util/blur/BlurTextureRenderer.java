package com.asteroid.duck.opengl.util.blur;

import com.asteroid.duck.opengl.util.AbstractPassthruRenderer;
import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import com.asteroid.duck.opengl.util.resources.shader.ShaderSource;
import com.asteroid.duck.opengl.util.resources.shader.vars.ShaderVariable;
import com.asteroid.duck.opengl.util.resources.shader.vars.ShaderVariables;
import com.asteroid.duck.opengl.util.resources.texture.Texture;
import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Renders a texture to the current output with an optional single-axis Gaussian blur.
 *
 * <p>The blur is implemented as a separable two-pass filter: run one instance with the X axis
 * enabled, write the result to an intermediate texture, then run a second instance with the Y axis
 * enabled. Alternatively, use {@link OffscreenBlurTextureRenderer} which wires both passes
 * together automatically.</p>
 *
 * <p>The kernel is computed by {@link BlurKernel} and collapsed to a linear-interpolation
 * optimised {@link DiscreteSampleKernel} that halves the number of texture fetches per fragment.
 * The kernel is recomputed whenever {@link #setKernelSize} is called.</p>
 *
 * <p>Blur can be toggled off at runtime via {@link #setBlur(boolean)} or {@link #toggleBlur()},
 * which causes the fragment shader to pass the source texel through unchanged (still multiplied
 * by the {@code multiplier} uniform for feedback-style effects).</p>
 */
public class BlurTextureRenderer extends AbstractPassthruRenderer {
	private static final Logger LOG = LoggerFactory.getLogger(BlurTextureRenderer.class);

	/** Minimum odd kernel size supported by {@link BlurKernel}. */
	public static final int MIN_KERNEL_SIZE = 3;
	/** Maximum odd kernel size; keeps the discrete tap count within MAX_KERNEL_SIZE in the shader. */
	public static final int MAX_KERNEL_SIZE = 65;

	private boolean blur = true;
	// If true = X, else Y
	private boolean axis = true;
	private final String textureName;
	private final ShaderVariables variables = new ShaderVariables();

	// language=GLSL
	private static final String VERTEX_SHADER = """
			#version 330
			in vec2 screenPosition;
			in vec2 texturePosition;
			out vec2 texCoords;

			void main() {
			    gl_Position = vec4(screenPosition, 0.0, 1.0);
			    texCoords = texturePosition;
			}
			""";

	// language=GLSL
	private static final String FRAGMENT_SHADER = """
			#version 460
			#define MAX_KERNEL_SIZE 64

			precision mediump float;

			uniform sampler2D tex;
			in vec2 texCoords;
			out vec4 fragColor;
			uniform bool x;
			uniform bool blur;
			uniform float multiplier = 0.99;
			uniform int kernelSize;
			uniform float offsets[MAX_KERNEL_SIZE];
			uniform float weights[MAX_KERNEL_SIZE];
			uniform vec2 dimensions;

			void main() {
			    fragColor = texture(tex, texCoords) * (blur ? weights[0] : 1.0);
			    if (blur) {
			        float dimension = x ? dimensions.x : dimensions.y;
			        for (int i = 1; i < kernelSize; i++) {
			            float delta = offsets[i] / dimension;
			            if (x) {
			                fragColor += texture(tex, (texCoords + vec2(delta, 0.0))) * weights[i];
			                fragColor += texture(tex, (texCoords - vec2(delta, 0.0))) * weights[i];
			            } else {
			                fragColor += texture(tex, (texCoords + vec2(0.0, delta))) * weights[i];
			                fragColor += texture(tex, (texCoords - vec2(0.0, delta))) * weights[i];
			            }
			        }
			    }
			    fragColor *= multiplier;
			}
			""";

	private int kernelSize = 29;
	private DiscreteSampleKernel cachedKernel = new BlurKernel(kernelSize).getDiscreteSampleKernel();

	/**
	 * Create a blur renderer that samples from the named texture.
	 *
	 * @param sourceTexture the {@link com.asteroid.duck.opengl.util.resources.manager.ResourceManager}
	 *                      key of the texture to blur; the texture must be registered before
	 *                      {@link #init} is called
	 */
	public BlurTextureRenderer(String sourceTexture) {
		this.textureName = sourceTexture;
	}

	@Override
	public void init(RenderContext ctx) throws IOException {
		super.init(ctx);
	}

	@Override
	protected Texture initTexture(RenderContext ctx) {
		return ctx.getResourceManager().getTexture(textureName);
	}

	@Override
	protected ShaderProgram initShaderProgram(RenderContext ctx) throws IOException {
		addVariable(ShaderVariable.booleanVariable("blur", this::isBlur));
		addVariable(ShaderVariable.booleanVariable("x", this::isXAxis));
		addVariable(ShaderVariable.vec2fVariable("dimensions", () -> {
			Texture t = ctx.getResourceManager().getTexture(textureName);
			return new Vector2f(t.getWidth(), t.getHeight());
		}));
		addVariable(ShaderVariable.intVariable("kernelSize", ctx2 -> cachedKernel.size()));
		addVariable(ShaderVariable.floatArrayVariable("offsets", () -> cachedKernel.floatOffsets()));
		addVariable(ShaderVariable.floatArrayVariable("weights", () -> cachedKernel.floatWeights()));
		return ShaderProgram.compile(
				ShaderSource.fromClass(VERTEX_SHADER, BlurTextureRenderer.class),
				ShaderSource.fromClass(FRAGMENT_SHADER, BlurTextureRenderer.class),
				null);
	}

	/**
	 * Register an additional {@link ShaderVariable} whose value will be pushed to the shader
	 * on every render. Use this to drive custom uniforms (e.g. the {@code multiplier} decay
	 * factor) from external state without subclassing.
	 *
	 * @param var the variable binding to add; must not be {@code null}
	 */
	public void addVariable(ShaderVariable<?> var) {
		variables.add(var);
	}

	@Override
	public void doRenderWithShader(RenderContext ctx) {
		variables.updateForRender(ctx, shaderProgram);
		super.doRenderWithShader(ctx);
	}

	/**
	 * Returns {@code true} if the Gaussian blur pass is currently active.
	 * When {@code false}, the fragment shader passes source texels through unchanged
	 * (still subject to the {@code multiplier} decay).
	 *
	 * @return {@code true} if blurring is enabled
	 */
	public boolean isBlur() {
    return blur;
  }

	/**
	 * Enable or disable the blur pass.
	 *
	 * @param blur {@code true} to apply the Gaussian kernel; {@code false} to pass through
	 */
	public void setBlur(boolean blur) {
		this.blur = blur;
	}

	/**
	 * Returns {@code true} if blurring along the X (horizontal) axis, {@code false} for Y (vertical).
	 *
	 * @return {@code true} for horizontal blur, {@code false} for vertical
	 */
	public boolean isXAxis() {
    return axis;
  }

	/**
	 * Set which axis to blur along. Combine with a second pass on the other axis for a full 2-D blur.
	 *
	 * @param axis {@code true} for horizontal (X) blur; {@code false} for vertical (Y) blur
	 */
	public void setXAxis(boolean axis) {
    this.axis = axis;
  }

	/** Toggle the blur on/off. Prints the new state to stdout (useful for key-binding debug). */
	public void toggleBlur() {
		blur = !blur;
		LOG.debug("Blur={}", blur);
	}

	/** Toggle between X and Y axis. Prints the new axis to stdout (useful for key-binding debug). */
	public void toggleAxis() {
		axis = !axis;
		LOG.debug("Axis={}", axis ? "x" : "y");
	}

	/**
	 * Returns the current odd kernel size (e.g. 29). A larger size produces a wider, softer blur
	 * at the cost of more per-fragment texture samples.
	 *
	 * @return the kernel size; always odd and in [{@value #MIN_KERNEL_SIZE}, {@value #MAX_KERNEL_SIZE}]
	 */
	public int getKernelSize() {
		return kernelSize;
	}

	/**
	 * Set the blur kernel size. The value is clamped to [{@value #MIN_KERNEL_SIZE},
	 * {@value #MAX_KERNEL_SIZE}] and rounded up to the next odd number if even. A new
	 * {@link DiscreteSampleKernel} is computed immediately if the size changed.
	 *
	 * @param size desired kernel size in texels; must be a positive odd integer within range
	 */
	public void setKernelSize(int size) {
		if (size < MIN_KERNEL_SIZE) size = MIN_KERNEL_SIZE;
		if (size > MAX_KERNEL_SIZE) size = MAX_KERNEL_SIZE;
		if (size % 2 == 0) size++;
		if (size != kernelSize) {
			kernelSize = size;
			cachedKernel = new BlurKernel(kernelSize).getDiscreteSampleKernel();
			LOG.info("Blur kernel size={} ({} discrete samples)", kernelSize, cachedKernel.size());
		}
	}

	/**
	 * Increase the kernel size by 2 (the next larger odd value).
	 * Clamped at {@value #MAX_KERNEL_SIZE}.
	 */
	public void increaseKernelSize() {
		setKernelSize(kernelSize + 2);
	}

	/**
	 * Decrease the kernel size by 2 (the next smaller odd value).
	 * Clamped at {@value #MIN_KERNEL_SIZE}.
	 */
	public void decreaseKernelSize() {
		setKernelSize(kernelSize - 2);
	}
}
