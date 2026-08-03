package com.asteroid.duck.opengl.util.resources.manager;

import com.asteroid.duck.opengl.util.resources.Resource;
import com.asteroid.duck.opengl.util.resources.bound.Binder;
import com.asteroid.duck.opengl.util.resources.bound.ExclusivityGroup;
import com.asteroid.duck.opengl.util.resources.io.Loader;
import com.asteroid.duck.opengl.util.resources.shader.ShaderLoader;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import com.asteroid.duck.opengl.util.resources.texture.io.TextureData;
import com.asteroid.duck.opengl.util.resources.texture.Texture;
import com.asteroid.duck.opengl.util.resources.texture.TextureFactory;
import com.asteroid.duck.opengl.util.resources.texture.TextureUnit;
import com.asteroid.duck.opengl.util.resources.texture.io.ImageLoadingOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

/**
 * Filesystem-aware resource loader and cache. This class is responsible for:
 * - resolving filesystem paths relative to a project root,
 * - delegating concrete loading to TextureFactory and ShaderLoader,
 * - caching loaded resources by logical name,
 * - allocating texture units.
 *
 * <p>This class intentionally focuses on loading/caching behavior only. It is separate
 * from the simple {@link ResourceListManager} which is a generic container of Resource instances.
 */
public class ResourceManagerImpl implements Resource, ResourceManager {
	private static final Logger LOG = LoggerFactory.getLogger(ResourceManagerImpl.class);

	private final ShaderLoader shaderLoader;
	private final TextureFactory textureFactory;

	// per-type named managers
	private final NamedResourceManager<Texture> textures = new NamedResourceManager<>();
	private final NamedResourceManager<ShaderProgram> shaders = new NamedResourceManager<>();
	private final NamedResourceManager<TextureUnit> textureUnits = new NamedResourceManager<>();
	// catch-all for resources that don't fit a named category
	private final ResourceListManager<Resource> others = new ResourceListManager<>();

	private final SortedSet<Integer> unallocatedTextureUnits = new TreeSet<>();

	// exclusivity groups by resource type
	private final Map<Class<?>, ExclusivityGroup<?>> exclusivityGroups = new HashMap<>();

	/**
	 * Create a resource manager rooted at the given loader.
	 *
	 * <p>Sub-loaders for textures ({@code textures/}) and shaders ({@code glsl/}) are derived
	 * automatically. All available {@link com.asteroid.duck.opengl.util.resources.bound.Binder}
	 * implementations are discovered via {@link java.util.ServiceLoader} and registered as
	 * exclusivity groups.</p>
	 *
	 * @param loader the root resource loader used to resolve all asset paths
	 */
	public ResourceManagerImpl(Loader loader) {
		this.textureFactory = new TextureFactory(loader.atPath("textures"));
		this.shaderLoader = new ShaderLoader(loader.atPath("glsl"));
		// populate the texture-unit pool
		initTextureUnits();
		loadBinders();
	}


	/**
	 * Initialize or reset the texture-unit pool to 0..31.
	 */
	private void initTextureUnits() {
		unallocatedTextureUnits.clear();
		for (int i = 1; i < 32; i++) {
			unallocatedTextureUnits.add(i);
		}
	}


	private void loadBinders() {
		@SuppressWarnings("rawtypes")
		ServiceLoader<Binder> loader = ServiceLoader.load(Binder.class);
		for (Binder<?> binder : loader) {
			var type = binder.resourceType();
			var name = binder.resourceType().getName();
			if (exclusivityGroups.containsKey(type)) {
				LOG.warn("Multiple binders found for resource type {}", name);
			} else {
				ExclusivityGroup<?> group = new ExclusivityGroup<>(this, binder);
				exclusivityGroups.put(type, group);
				if (LOG.isDebugEnabled()) {
					LOG.debug("Loaded binder and exclusivity group for resource type {}", name);
				}
			}
		}
	}

	/**
	 * Returns the TextureFactory used by this manager.
	 *
	 * @return the configured {@link TextureFactory}
	 */
	public TextureFactory getTextureFactory() {
		return textureFactory;
	}

	/**
	 * Returns the {@link ShaderLoader} used by this manager.
	 *
	 * @return the configured {@link ShaderLoader}
	 */
	public ShaderLoader getShaderLoader() {
		return shaderLoader;
	}

	public Texture getTexture(String name) {
		Texture t = textures.get(name);
		if (t == null) {
			throw new IllegalArgumentException("No such resource as " + name);
		}
		return t;
	}

	public void putTexture(String name, Texture texture) {
		textures.put(name, texture);
	}

	public Texture getTexture(String name, String path) {
		return getTexture(name, path, ImageLoadingOptions.DEFAULT);
	}

	public Texture getTexture(String name, String path, ImageLoadingOptions options) {
		if (!textures.contains(name)) {
			if(LOG.isTraceEnabled()) {
                LOG.trace("Trying to load resource {}", name);
			}
			Texture tex = null;
			if (path != null) {
				try {
					tex = textureFactory.LoadTexture(path, options);
				} catch (IOException e) {
					LOG.error("Error loading texture", e);
				}
			}
			textures.put(name, tex);
		}
		return textures.get(name);
	}

	public TextureData loadTextureData(String image, ImageLoadingOptions options) throws IOException {
		return textureFactory.loadTextureData(image, options);
	}

	public ShaderProgram getShader(String name, String vertexPath, String fragPath, String geomPath) {
		if (!shaders.contains(name)) {
			if (LOG.isTraceEnabled()) {
                LOG.trace("Loading shader: {}", name);
			}
			try {
				ShaderProgram shader = shaderLoader.LoadShaderProgram(vertexPath, fragPath, geomPath);
				shaders.put(name, shader);
			} catch (IOException e) {
				LOG.error("Error loading shader", e);
			}
		}
		return shaders.get(name);
	}

	public ShaderProgram getSimpleShader(String name) {
		return getShader(name, name + "/vertex.glsl", name + "/frag.glsl", null);
	}

	public TextureUnit nextTextureUnit() {
		int index = unallocatedTextureUnits.first();
		unallocatedTextureUnits.remove(index);
		TextureUnit unit = TextureUnit.index(index, this::replaceTextureUnit);
		textureUnits.put(Integer.toHexString(unit.getIndex()), unit);
		if (LOG.isTraceEnabled()) {
			LOG.trace("Allocated next texture unit: {}", unit);
		}
		return unit;
	}

	private void replaceTextureUnit(TextureUnit textureUnit) {
		int index = textureUnit.getIndex();
		String key = Integer.toHexString(index);
		// remove mapping from the named manager and unregister from owner
		// removeByName will also stop tracking the resource via ResourceManager.remove
		textureUnits.removeByName(key);
		unallocatedTextureUnits.add(index);
	}

	public Stream<TextureUnit> textureUnits() {
		return textureUnits.entries().map(java.util.Map.Entry::getValue);
	}


	@Override
	public <T extends Resource> ExclusivityGroup<T> exclusivityGroup(Class<T> type) {
		@SuppressWarnings("unchecked")
		ExclusivityGroup<T> group = (ExclusivityGroup<T>) exclusivityGroups.get(type);
		if (group == null) {
			throw new IllegalArgumentException("No exclusivity group for resource type " + type.getName());
		}
		return group;
	}

	@Override
	public void register(Resource resource) {
		others.add(resource);
	}

	/**
	 * Destroy and clear all managed resources. After calling this method the manager will have no cached resources.
	 *
	 * Note: this replaces the former clear() method.
	 */
	@Override
	public void dispose() {
		// Each NamedResourceManager tracks its own resources; destroy them to
		// ensure proper disposal.
		textures.dispose();
		shaders.dispose();
		textureUnits.dispose();
		others.dispose();

		initTextureUnits();
	}


}
