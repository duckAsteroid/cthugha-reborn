package com.asteroid.duck.opengl.util.resources.texture;

import static org.lwjgl.opengl.GL11.*;

/** How the texture wraps - repeat or stop at edge */
public enum Wrap implements OpenGLCoded {
	CLAMP_TO_EDGE(GL_CLAMP), REPEAT(GL_REPEAT);
	private final int openGlCode;

	Wrap(int openGlCode) {
		this.openGlCode = openGlCode;
	}

	public int openGlCode() {
		return openGlCode;
	}

	public int[] openGlParams() {
		return new int[]{GL_TEXTURE_WRAP_S, GL_TEXTURE_WRAP_T};
	}
}
