package com.asteroid.duck.opengl.util.resources.shader.vars;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.Comparator;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Binds a lazily-evaluated value to a named {@link com.asteroid.duck.opengl.util.resources.shader.Uniform}
 * in a {@link ShaderProgram}, fetching the value fresh on every render call.
 *
 * <p>Use the static factory methods ({@link #booleanVariable}, {@link #floatVariable},
 * {@link #vec2fVariable}, etc.) to create concrete bindings without subclassing. Each factory
 * accepts either a plain {@link java.util.function.Supplier} or a
 * {@link java.util.function.Function}{@code <RenderContext, T>} for values that depend on
 * per-frame render state.</p>
 *
 * @param <T> the Java type of the value supplied to the shader uniform (e.g. {@link Boolean},
 *            {@link Float}, {@link org.joml.Vector2f}, or {@code float[]})
 */
public abstract class ShaderVariable<T> implements BiConsumer<RenderContext, ShaderProgram> {
	/** The name of the GLSL uniform that this variable is bound to. */
	protected final String name;
	/** Supplies the uniform value on each render call, optionally using per-frame {@link RenderContext} state. */
	protected final Function<RenderContext, T> provider;
	/** Reserved for a future caching optimisation; currently always {@code false}. */
	protected final boolean cached = false;

	/**
	 * Create a shader variable binding.
	 *
	 * @param name     the exact GLSL uniform name to write to on each render
	 * @param provider a function that returns the current value; called once per render frame
	 */
	protected ShaderVariable(String name, Function<RenderContext, T> provider) {
		this.name = name;
		this.provider = provider;
	}

	/**
	 * Converts a supplier into a function that ignores the supplied RenderContext.
	 * Allows "dumb" suppliers to be used where a function is required in {@link #provider}.
	 * @param supplier the simple supplier to wrap.
	 * @return a function that ignores the RenderContext and calls the supplier.
	 * @param <T> the type of value supplied (for type safety)
	 */
	public static <T> Function<RenderContext, T> wrap(Supplier<T> supplier) {
		return (ctx) -> supplier.get();
	}

	/**
	 * Create a boolean uniform binding backed by a plain supplier (no render context needed).
	 *
	 * @param name     the GLSL uniform name
	 * @param supplier supplies the boolean value each frame
	 * @return a new {@link ShaderVariable} that sets a {@code bool} uniform
	 */
	public static ShaderVariable<Boolean> booleanVariable(String name, Supplier<Boolean> supplier) {
		return booleanVariable(name, wrap(supplier));
	}

	/**
	 * Create a boolean uniform binding backed by a render-context-aware function.
	 *
	 * @param name     the GLSL uniform name
	 * @param provider function that returns the boolean value given the current render context
	 * @return a new {@link ShaderVariable} that sets a {@code bool} uniform
	 */
	public static ShaderVariable<Boolean> booleanVariable(String name, Function<RenderContext, Boolean> provider) {
    return new ShaderVariable<>(name, provider) {
	    public void accept(RenderContext ctx, ShaderProgram program) {
		    program.uniforms().get(name, Boolean.class).set(provider.apply(ctx));
	    }
    };
  }

	/**
	 * Create a float uniform binding backed by a plain supplier.
	 *
	 * @param name     the GLSL uniform name
	 * @param supplier supplies the float value each frame
	 * @return a new {@link ShaderVariable} that sets a {@code float} uniform
	 */
	public static ShaderVariable<Float> floatVariable(String name, Supplier<Float> supplier) {
		return floatVariable(name, wrap(supplier));
	}

	/**
	 * Create a float uniform binding backed by a render-context-aware function.
	 *
	 * @param name     the GLSL uniform name
	 * @param provider function that returns the float value given the current render context
	 * @return a new {@link ShaderVariable} that sets a {@code float} uniform
	 */
	public static ShaderVariable<Float> floatVariable(String name, Function<RenderContext, Float> provider) {
		return new ShaderVariable<>(name, provider) {
			public void accept(RenderContext ctx, ShaderProgram program) {
				program.uniforms().get(name, Float.class).set(provider.apply(ctx));
			}
		};
	}

	/**
	 * Create an integer uniform binding backed by a render-context-aware function.
	 *
	 * @param name     the GLSL uniform name
	 * @param provider function that returns the integer value given the current render context
	 * @return a new {@link ShaderVariable} that sets an {@code int} uniform
	 */
	public static ShaderVariable<Integer> intVariable(String name, Function<RenderContext, Integer> provider) {
		return new ShaderVariable<>(name, provider) {
			public void accept(RenderContext ctx, ShaderProgram program) {
				program.uniforms().get(name, Integer.class).set(provider.apply(ctx));
			}
		};
	}

	/**
	 * Create a {@code vec2} uniform binding backed by a plain supplier.
	 *
	 * @param name     the GLSL uniform name
	 * @param supplier supplies the 2-component float vector each frame
	 * @return a new {@link ShaderVariable} that sets a {@code vec2} uniform
	 */
	public static ShaderVariable<Vector2f> vec2fVariable(String name, Supplier<Vector2f> supplier) {
		return vec2fVariable(name, wrap(supplier));
	}

	/**
	 * Create a {@code vec2} uniform binding backed by a render-context-aware function.
	 *
	 * @param name     the GLSL uniform name
	 * @param provider function that returns the 2-component vector given the current render context
	 * @return a new {@link ShaderVariable} that sets a {@code vec2} uniform
	 */
	public static ShaderVariable<Vector2f> vec2fVariable(String name, Function<RenderContext, Vector2f> provider) {
		return new ShaderVariable<>(name, provider) {
			public void accept(RenderContext ctx, ShaderProgram program) {
				program.uniforms().get(name, Vector2f.class).set(provider.apply(ctx));
			}
		};
	}

	/**
	 * Create a {@code vec4} uniform binding backed by a render-context-aware function.
	 *
	 * @param name     the GLSL uniform name
	 * @param provider function that returns the 4-component float vector given the current render context
	 * @return a new {@link ShaderVariable} that sets a {@code vec4} uniform
	 */
	public static ShaderVariable<Vector4f> vec4fVariable(String name, Function<RenderContext, Vector4f> provider) {
		return new ShaderVariable<>(name, provider) {
			public void accept(RenderContext ctx, ShaderProgram program) {
				program.uniforms().get(name, Vector4f.class).set(provider.apply(ctx));
			}
		};
	}

	/**
	 * Create a float-array uniform binding backed by a plain supplier.
	 * Useful for passing arrays to GLSL, e.g. convolution kernel weights.
	 *
	 * @param name     the GLSL uniform array name
	 * @param supplier supplies the float array each frame
	 * @return a new {@link ShaderVariable} that sets a {@code float[]} uniform
	 */
	public static ShaderVariable<float[]> floatArrayVariable(String name, Supplier<float[]> supplier) {
		return new ShaderVariable<>(name, wrap(supplier)) {
			public void accept(RenderContext ctx, ShaderProgram program) {
				program.uniforms().get(name, float[].class).set(provider.apply(ctx));
			}
		};
	}
}
