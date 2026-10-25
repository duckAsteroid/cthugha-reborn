package com.asteroid.duck.opengl.util.resources.texture;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_1D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;

/** The basic dimensions of a texture: 1- or 2-dimensional */
public enum Dimensions implements OpenGLCoded {
	/** A 1D Texture */
	ONE_DIMENSION(GL_TEXTURE_1D),
	/** A 2D Texture */
	TWO_DIMENSION(GL_TEXTURE_2D);

	private final int openGlCode;

	Dimensions(int openGlCode) {
		this.openGlCode = openGlCode;
	}

	public int openGlCode() {
		return openGlCode;
	}

	public int[] openGlParams() {
		return new int[]{};
	}
}
