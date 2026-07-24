package com.asteroid.duck.opengl.util.resources.shader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;

/**
 * A uniform is a shader wide (not vertex specific) variable used in the shader pipeline.
 * This class allows setting the value of the uniform in a type-safe way.
 * @param <T> The java type of the uniform
 */
public class Uniform<T> {
	private static final Logger LOG = LoggerFactory.getLogger(Uniform.class);
	private final Uniforms owner;
	private final String name;
	private final int location;
	private final BiConsumer<Integer, T> setter;

	/**
	 * Package-private constructor to be used by {@link Uniforms}
	 * @param name the name of the uniform
	 * @param location the location of the uniform in the shader program
	 * @param setter a function that can set the uniform's value, taking location and the new value
	 */
	Uniform(Uniforms owner, String name, int location, BiConsumer<Integer, T> setter) {
		this.owner = owner;
		this.name = name;
		this.location = location;
		this.setter = setter;
	}

	public int getLocation() {
		return location;
	}

	public String getName() {
		return name;
	}

	/**
	 * Sets the value of this uniform.
	 * @param value the new value
	 */
	public void set(T value) {
		if(LOG.isTraceEnabled()) {
			LOG.trace("Setting uniform '{}' @ {} in {} to value: {}", name, location, owner.getShaderProgram().shortDebugName(), value);
		}
		setter.accept(location, value);
	}

	public Runnable setLater(T value) {
		owner.getShaderProgram();
		return () -> set(value);
	}
}