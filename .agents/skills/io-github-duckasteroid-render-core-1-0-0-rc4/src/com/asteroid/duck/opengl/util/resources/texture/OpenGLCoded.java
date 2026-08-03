package com.asteroid.duck.opengl.util.resources.texture;

import com.asteroid.duck.opengl.util.GLCoded;

import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * An interface that represents an OpenGL object that has an integer code.
 * This interface provides methods to retrieve the OpenGL code and parameters associated with the object.
 */
public interface OpenGLCoded extends GLCoded {

	/**
	 * Returns an array of OpenGL parameters associated with this object.
	 *
	 * @return an array of OpenGL parameters
	 */
	int[] openGlParams();

	@Override
	default Integer get() {
		return openGlCode();
	}

	/**
	 * Returns a stream of OpenGL parameters used to apply this code
	 *
	 * @return a stream of OpenGL parameters
	 */
	default IntStream openGlParamsStream() {
		return IntStream.of(openGlParams());
	}
}
