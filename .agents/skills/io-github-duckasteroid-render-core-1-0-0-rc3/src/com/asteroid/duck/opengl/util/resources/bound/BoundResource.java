package com.asteroid.duck.opengl.util.resources.bound;

import com.asteroid.duck.opengl.util.resources.Resource;

/**
 * A handle representing a resource that is currently bound within an {@link ExclusivityGroup}.
 *
 * <p>Returned by {@link ExclusivityGroup#bind} when a resource is successfully made active.
 * Callers should hold this handle for the duration they need the resource bound and call
 * {@link #unbind()} when they are done, allowing the next caller to bind a different resource
 * of the same type. Failing to unbind is not catastrophic — the next {@code bind()} call will
 * replace the active resource anyway — but it is good practice for clarity and debugging.</p>
 *
 * @param <T> the type of GL resource being held
 */
public class BoundResource<T extends Resource> {
    private final ExclusivityGroup<T> exclusivityGroup;
    private final T resource;

    /**
     * Package-private constructor; instances are created only by {@link ExclusivityGroup#bind}.
     *
     * @param exclusivityGroup the group that owns the binding lifecycle for this resource type
     * @param resource         the resource that was bound
     */
    public BoundResource(ExclusivityGroup<T> exclusivityGroup, T resource) {
        this.exclusivityGroup = exclusivityGroup;
        this.resource = resource;
    }

    /**
     * Release this resource from its {@link ExclusivityGroup}, allowing another resource of the
     * same type to be bound.
     *
     * @return {@code true} if the resource was successfully unbound; {@code false} if a
     *         {@link BindingException} occurred (the resource may still be considered bound by GL)
     */
    public boolean unbind() {
        try {
            exclusivityGroup.unbind(resource);
            return true;
        }
        catch(BindingException e) {
            return false;
        }
    }
}
