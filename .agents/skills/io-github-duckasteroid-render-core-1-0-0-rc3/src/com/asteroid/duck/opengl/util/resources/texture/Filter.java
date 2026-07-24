package com.asteroid.duck.opengl.util.resources.texture;

import static org.lwjgl.opengl.GL11.*;

/**
 * How the texture is filtered when magnified or minified.
 * LINEAR interpolation or NEAREST neighbour
 */
public enum Filter implements OpenGLCoded {
    /** Bilinear interpolation: smooths between adjacent texels; ideal for most 2D rendering. */
	LINEAR(GL_LINEAR),
    /** Nearest-neighbour selection: returns the single closest texel with no blending; preserves sharp pixel edges. */
	NEAREST(GL_NEAREST);

	private final int openGlCode;

	Filter(int openGlCode) {
		this.openGlCode = openGlCode;
	}

	public int openGlCode() {
		return openGlCode;
	}

	public int[] openGlParams() {
		return new int[]{GL_TEXTURE_MIN_FILTER, GL_TEXTURE_MAG_FILTER};
	}
}
