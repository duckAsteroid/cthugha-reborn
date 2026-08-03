package com.asteroid.duck.opengl.util.resources.bound;

import com.asteroid.duck.opengl.util.resources.Resource;

/**
 * A binder binds and unbinds resources. Typically, this means calling OpenGL bind and unbind functions.
 * @param <T> the type of resource to bind
 */
public interface Binder<T extends Resource> {
    /**
     * The type of resource this binder binds.
     * @return the resource type
     */
    Class<T> resourceType();
    /**
     * Binds the resource.
     * @param resource the resource to bind
     * @throws BindingException if binding fails
     */
    void bind(T resource) throws BindingException;

    /**
     * Unbinds the resource.
     * If there is a default resource, it is returned.
     * @param resource the resource to unbind
     * @return the default resource, or null if there is none
     * @throws BindingException if unbinding fails
     */
    T unbind(T resource) throws BindingException;
}
