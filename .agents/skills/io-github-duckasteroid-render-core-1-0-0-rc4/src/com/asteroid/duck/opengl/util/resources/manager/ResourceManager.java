package com.asteroid.duck.opengl.util.resources.manager;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.resources.Resource;
import com.asteroid.duck.opengl.util.resources.bound.BindingContext;
import com.asteroid.duck.opengl.util.resources.shader.ShaderLoader;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import com.asteroid.duck.opengl.util.resources.texture.io.TextureData;
import com.asteroid.duck.opengl.util.resources.texture.Texture;
import com.asteroid.duck.opengl.util.resources.texture.TextureFactory;
import com.asteroid.duck.opengl.util.resources.texture.TextureUnit;
import com.asteroid.duck.opengl.util.resources.texture.io.ImageLoadingOptions;

import java.io.IOException;
import java.util.stream.Stream;

/**
 * API surface for resource managers that provide named access to textures, shaders and texture-units.
 *
 * Implementations (for example FileResourceManager) should provide the concrete behavior for loading,
 * caching and lifetime management.
 *
 * @see RenderContext
 */
public interface ResourceManager extends Resource, BindingContext {
	/**
	 * Retrieve a previously stored texture by logical name.
	 *
	 * @param name the name under which the texture was registered
	 * @return the texture, or {@code null} if none is registered under that name
	 */
	Texture getTexture(String name);

	/**
	 * Store a texture under a logical name so other components can retrieve it.
	 *
	 * @param name    the logical name for the texture
	 * @param texture the texture to register
	 */
	void putTexture(String name, Texture texture);

	/**
	 * Load a texture from the given classpath path and register it under {@code name}.
	 * Returns the cached copy if the name was already registered.
	 *
	 * @param name the logical name
	 * @param path classpath-relative path to the image file
	 * @return the loaded (or cached) texture
	 */
	Texture getTexture(String name, String path);

	/**
	 * Load a texture from the given path with specific loading options and cache it by name.
	 *
	 * @param name    the logical name
	 * @param path    classpath-relative path to the image file
	 * @param options controls flip, format, and other loading behaviour
	 * @return the loaded (or cached) texture
	 */
	Texture getTexture(String name, String path, ImageLoadingOptions options);

	/**
	 * Load raw texture data (pixels + dimensions) from the given path without allocating a GL texture.
	 * Useful when the caller needs to inspect or upload the pixels manually.
	 *
	 * @param image   classpath-relative image path
	 * @param options loading options
	 * @return the loaded pixel data
	 * @throws IOException if the image cannot be read
	 */
	TextureData loadTextureData(String image, ImageLoadingOptions options) throws IOException;

	/**
	 * Returns the {@link TextureFactory} backed by this manager's asset loader.
	 *
	 * @return the texture factory; never {@code null}
	 */
	TextureFactory getTextureFactory();

	/**
	 * Load and compile a full shader program from separate vertex, fragment and optional geometry source files.
	 * The result is cached by {@code name}.
	 *
	 * @param name       logical cache key
	 * @param vertexPath classpath-relative path to the vertex shader
	 * @param fragPath   classpath-relative path to the fragment shader
	 * @param geomPath   classpath-relative path to the geometry shader, or {@code null} to omit
	 * @return the compiled and linked shader program
	 */
	ShaderProgram getShader(String name, String vertexPath, String fragPath, String geomPath);

	/**
	 * Load and compile a shader program whose sources follow the {@code <name>/vertex.glsl} and
	 * {@code <name>/frag.glsl} naming convention. Result is cached by {@code name}.
	 *
	 * @param name the shader directory name under the {@code glsl/} asset root
	 * @return the compiled and linked shader program
	 */
	ShaderProgram getSimpleShader(String name);

	/**
	 * Returns the {@link ShaderLoader} that handles source loading and include processing.
	 *
	 * @return the shader loader; never {@code null}
	 */
	ShaderLoader getShaderLoader();

	/**
	 * Allocate the next available texture unit from the pool.
	 * Texture units are numbered 1–31; unit 0 is reserved for the default binding.
	 *
	 * @return the next unused {@link TextureUnit}
	 * @throws IllegalStateException if all texture units are already allocated
	 */
	TextureUnit nextTextureUnit();

	/**
	 * Stream all texture units currently allocated by this manager.
	 *
	 * @return a stream of allocated texture units in allocation order
	 */
	Stream<TextureUnit> textureUnits();

	/**
	 * Register an arbitrary resource for lifecycle tracking. The resource's {@code dispose()}
	 * will be called when this manager is disposed, ensuring cleanup even for resources that
	 * don't fit into a named category (textures, shaders, texture units).
	 *
	 * @param resource the resource to track; null values are ignored
	 */
	void register(Resource resource);

}
