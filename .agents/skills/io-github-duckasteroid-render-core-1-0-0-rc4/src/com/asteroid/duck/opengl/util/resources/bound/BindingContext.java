package com.asteroid.duck.opengl.util.resources.bound;

import com.asteroid.duck.opengl.util.resources.Resource;

/**
 * A context for binding resources, it manages exclusivity groups for different resource types.
 * @see com.asteroid.duck.opengl.util.RenderContext
 */
public interface BindingContext {
    /**
     * Get the exclusivity group for the given resource type.
     * @param type the type of the bound resource
     * @return the exclusivity group for the given resource type
     * @throws IllegalArgumentException if the type is not a recognised bound resource type
     * @param <T> the type of the bound resource
     */
    <T extends Resource> ExclusivityGroup<T> exclusivityGroup(Class<T> type);
}
