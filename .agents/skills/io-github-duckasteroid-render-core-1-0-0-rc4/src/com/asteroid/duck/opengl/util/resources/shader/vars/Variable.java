package com.asteroid.duck.opengl.util.resources.shader.vars;

/**
 * Helper for holding data about a shader variable
 * @param type uniform or attribute
 * @param name the name
 * @param size the size of the variable
 * @param dataType the data type {@link ShaderVariableType}
 * @param location the index location in the shader
 */
public record Variable(VariableType type, String name, int size, ShaderVariableType dataType,
                       int location) implements Comparable<Variable> {
    /**
     * Sort by name
     */
	@Override
	public int compareTo(Variable o) {
		return this.name.compareTo(o.name);
	}
}
