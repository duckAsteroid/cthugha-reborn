package com.asteroid.duck.opengl.util.resources.manager;

import com.asteroid.duck.opengl.util.resources.Resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Extends {@link ResourceListManager} with a {@code String → Resource} name-lookup map.
 * Resources registered here are also tracked by the parent list manager, so they are disposed
 * automatically when {@link #dispose()} is called.
 *
 * <p>Example usage:</p>
 * <pre>
 * NamedResourceManager&lt;Texture&gt; nm = new NamedResourceManager&lt;&gt;();
 * nm.put("diffuse", texture);
 * Texture t = nm.get("diffuse");
 * </pre>
 *
 * @param <R> the type of {@link Resource} stored in this manager; all values in the map share
 *            this type, which allows the manager to dispose them uniformly at shutdown
 */
public class NamedResourceManager<R extends Resource> extends ResourceListManager<R> {
    /** Default constructor; creates an empty manager with no resources. */
    public NamedResourceManager() {}
    private final Map<String, R> map = Collections.synchronizedMap(new java.util.HashMap<>());

    /**
     * Associate a name with a resource and register it for disposal.
     *
     * @param name     logical name
     * @param resource resource instance (may be null)
     */
    public void put(String name, R resource) {
        map.put(name, resource);
        if (resource != null) {
            add(resource);
        }
    }

    /**
     * Retrieve a resource by name. May return null if not present.
     *
     * @param name logical name
     * @return the associated resource or null
     */
    public R get(String name) {
        return map.get(name);
    }

    /**
     * Remove the mapping for the given name and stop tracking the resource (does not destroy it).
     *
     * @param name logical name to remove
     * @return the removed resource or null
     */
    public R removeByName(String name) {
        R r = map.remove(name);
        if (r != null) {
            super.remove(r);
        }
        return r;
    }

    /**
     * Returns {@code true} if a resource is currently mapped under the given name.
     *
     * @param name the logical name to look up
     * @return {@code true} if the name is present in the map; {@code false} otherwise
     */
    public boolean contains(String name) {
        return map.containsKey(name);
    }

    /**
     * Stream a snapshot of all name-to-resource mappings currently in this manager.
     * The snapshot is taken under synchronisation so it reflects a consistent state.
     *
     * @return a stream of {@link java.util.Map.Entry} pairs; the stream is not backed by the live map
     */
    public Stream<Map.Entry<String, R>> entries() {
        synchronized (map) {
            return new ArrayList<>(map.entrySet()).stream();
        }
    }

    /**
     * When destroyed, delegate to parent to destroy tracked resources and clear name mappings.
     */
    @Override
    public void dispose() {
        super.dispose();
        map.clear();
    }
}
