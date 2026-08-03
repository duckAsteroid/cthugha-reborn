package com.asteroid.duck.opengl.util.blur;

import com.asteroid.duck.opengl.util.CompositeRenderItem;
import com.asteroid.duck.opengl.util.OffscreenTextureRenderer;
import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.resources.shader.vars.ShaderVariable;
import com.asteroid.duck.opengl.util.resources.texture.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A renderer that takes a texture and blurs it using a blur shader.
 * Each pass performs a two-stage separable Gaussian blur: X axis to an offscreen buffer (fbo_a),
 * then Y axis back to screen (final pass) or to a second offscreen buffer (fbo_b).
 * Passes tick-tock: fbo_b feeds the next pass's X input, avoiding read/write to the same texture.
 * Multiple passes multiply the effective blur radius (each pass adds sigma in quadrature).
 */
public class OffscreenBlurTextureRenderer extends CompositeRenderItem {
	private static final Logger LOG = LoggerFactory.getLogger(OffscreenBlurTextureRenderer.class);
	/** Default offscreen texture options: RGBA, linear filtering, repeat wrapping. */
	public static final TextureOptions STANDARD_TEXTURE_OPTS = new TextureOptions(DataFormat.RGBA, Filter.LINEAR, Wrap.REPEAT);

	private float multiplier = 0.99f;

	private final int passes;
	private final TextureOptions opts;
	private final String sourceTextureName;
	private final List<BlurTextureRenderer> stages = new ArrayList<>();

	/**
	 * Create a single-pass blur renderer with default texture options.
	 *
	 * @param source the logical name of the texture to blur (must be registered in the resource manager)
	 */
	public OffscreenBlurTextureRenderer(String source) {
		this(source, 1, STANDARD_TEXTURE_OPTS);
	}

	/**
	 * Create a single-pass blur renderer with custom texture options.
	 *
	 * @param source  the logical name of the texture to blur
	 * @param options offscreen texture format, filter, and wrap settings
	 */
	public OffscreenBlurTextureRenderer(String source, TextureOptions options) {
		this(source, 1, options);
	}

	/**
	 * Create a multi-pass blur renderer.
	 *
	 * @param source  the logical name of the texture to blur
	 * @param passes  number of full X+Y blur passes; each pass adds blur in quadrature so the
	 *                effective blur grows as √passes; must be ≥ 1
	 * @param options offscreen texture format, filter, and wrap settings
	 */
	public OffscreenBlurTextureRenderer(String source, int passes, TextureOptions options) {
		if (passes < 1) throw new IllegalArgumentException("passes must be >= 1");
		this.sourceTextureName = source;
		this.passes = passes;
		this.opts = options;
	}

	@Override
	public void init(RenderContext ctx) throws IOException {
		String fboA = sourceTextureName + "_fbo_a";
		String fboB = sourceTextureName + "_fbo_b";

		Texture texA = TextureFactory.createTexture(ctx.getWindow(), null, opts);
		ctx.getResourceManager().putTexture(fboA, texA);
		Texture texB = TextureFactory.createTexture(ctx.getWindow(), null, opts);
		ctx.getResourceManager().putTexture(fboB, texB);

		for (int i = 0; i < passes; i++) {
			boolean isLast = (i == passes - 1);
			String input = (i == 0) ? sourceTextureName : fboB;

			// X pass always renders to fbo_a
			BlurTextureRenderer xBlur = new BlurTextureRenderer(input);
			xBlur.setXAxis(true);
			xBlur.addVariable(ShaderVariable.floatVariable("multiplier", this::multiplier));
			stages.add(xBlur);
			add(new OffscreenTextureRenderer(xBlur, texA));

			// Y pass: intermediate passes render to fbo_b; final pass renders to screen
			BlurTextureRenderer yBlur = new BlurTextureRenderer(fboA);
			yBlur.setXAxis(false);
			yBlur.addVariable(ShaderVariable.floatVariable("multiplier", this::multiplier));
			stages.add(yBlur);
			if (isLast) {
				add(yBlur);
			} else {
				add(new OffscreenTextureRenderer(yBlur, texB));
			}
		}

		super.init(ctx);
	}

	/** Increase the Gaussian kernel size by 2 (next valid odd size). Propagated to all blur stages. */
	public void increaseKernelSize() {
		setKernelSize(getKernelSize() + 2);
	}

	/** Decrease the Gaussian kernel size by 2 (previous valid odd size, minimum 3). Propagated to all blur stages. */
	public void decreaseKernelSize() {
		setKernelSize(getKernelSize() - 2);
	}
	private int getKernelSize() {
		return stages.getFirst().getKernelSize();
	}

	private void setKernelSize(int size) {
		stages.forEach(s -> s.setKernelSize(size));
	}

	/**
	 * Returns the current per-frame brightness multiplier applied by the blur shader.
	 * Values below 1.0 cause the image to fade over time, creating a trail effect.
	 *
	 * @return the current multiplier; starts at 0.99
	 */
	public float multiplier() {
		return multiplier;
	}

	/**
	 * Scale the current multiplier by {@code v} (multiplicative adjustment).
	 * Use values slightly below 1.0 to darken gradually and slightly above 1.0 to brighten.
	 *
	 * @param v the scale factor applied to the current {@link #multiplier()}
	 */
	public void multiply(float v) {
		multiplier *= v;
		LOG.info("multiplier={}", multiplier);
	}

	/** Toggle the blur shader on or off for all stages (passes through texels unmodified when off). */
	public void toggleBlur() {
		stages.forEach(BlurTextureRenderer::toggleBlur);
	}
}
