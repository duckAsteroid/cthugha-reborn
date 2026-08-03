package com.asteroid.duck.opengl.util.resources.shader.vars;

import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL20.*;

/**
 * Helper to manage accessing uniform and attribute variable data from a compiled and linked shader
 */
public enum VariableType implements VariableEx {
	/**
	 * A vertex attribute - these change per-vertex being rendered
	 */
	ATTRIBUTE(GL_ACTIVE_ATTRIBUTES, GL_ACTIVE_ATTRIBUTE_MAX_LENGTH) {
		@Override
		public void readVariable(int program, int index, IntBuffer len, IntBuffer size, IntBuffer type, ByteBuffer name) {
			glGetActiveAttrib(program, index, len, size, type, name);
		}
	},
	/**
	 * A uniform variable (constant across a draw call)
	 */
	UNIFORM(GL_ACTIVE_UNIFORMS, GL_ACTIVE_UNIFORM_MAX_LENGTH) {
		@Override
		public void readVariable(int program, int index, IntBuffer len, IntBuffer size, IntBuffer type, ByteBuffer name) {
			glGetActiveUniform(program, index, len, size, type, name);
		}
	};

	/// The GL ID for count of this variable type
	protected final int counter;
	/// The GL ID for the max length of any name of this variable type
	protected final int nameLengther;

	VariableType(int counter, int nameLengther) {
		this.counter = counter;
		this.nameLengther = nameLengther;
	}

	@Override
	public int maxNameLength(int program) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer tmp = stack.mallocInt(1);
			glGetProgramiv(program, nameLengther, tmp);
			return tmp.get();
		}
	}

	@Override
	public int count(int program) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer tmp = stack.mallocInt(1);
			glGetProgramiv(program, counter, tmp);
			return tmp.get();
		}
	}

	@Override
	public abstract void readVariable(int program, int index, IntBuffer len, IntBuffer size, IntBuffer type, ByteBuffer value);
}
