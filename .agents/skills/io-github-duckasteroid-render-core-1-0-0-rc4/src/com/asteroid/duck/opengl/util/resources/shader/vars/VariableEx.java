package com.asteroid.duck.opengl.util.resources.shader.vars;

import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Interface between {@link VariableType} and {@link ShaderProgram} - provides methods to read variable data
 */
public interface VariableEx {
	/**
	 * Get the max name length for any variable of this type in the given program
	 * @param program the GL program ID
	 * @return the max name length
	 */
	int maxNameLength(int program);

	/**
	 * Get the count of variables of this type in the given program
	 * @param program the GL program ID
	 * @return the count of variables
	 */
	int count(int program);

	/**
	 * Read the variable data at the given index in the given program
	 * @param program the GL program ID
	 * @param index the index of the variable to read
	 * @param len buffer to hold the length of the name
	 * @param size buffer to hold the size of the variable
	 * @param type buffer to hold the data type of the variable
	 * @param value buffer to hold the name of the variable
	 */
	void readVariable(int program, int index, IntBuffer len, IntBuffer size, IntBuffer type, ByteBuffer value);
}
