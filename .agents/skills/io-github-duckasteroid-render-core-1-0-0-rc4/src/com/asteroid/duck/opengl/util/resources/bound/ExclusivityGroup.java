package com.asteroid.duck.opengl.util.resources.bound;

import com.asteroid.duck.opengl.util.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An exclusivity group manages the binding of resources of a specific type, ensuring that only one resource of that
 * type is bound at any given time.
 * @param <T> the type of resource managed by this exclusivity group
 */
public class ExclusivityGroup<T extends Resource> {
    private static final Logger LOG = LoggerFactory.getLogger(ExclusivityGroup.class);
    // The binding context this exclusivity group belongs to
    private final BindingContext bindingContext;
    // The binder used to bind and unbind resources of type T
    private final Binder<T> binder;
    // The currently bound resource of type T, or null if no resource is bound
    private T boundResource = null;
    // A lock to ensure thread-safe access to the binding and unbinding operations
    private final ReentrantLock lock = new ReentrantLock(false);

    /**
     * Create an exclusivity group that delegates bind/unbind operations to the given binder.
     *
     * @param bindingContext the context this group belongs to; passed through to {@link BoundResource}
     *                       so callers can access context-level services from the bound handle
     * @param binder         the GL-level binder for resources of type {@code T}
     */
    public ExclusivityGroup(BindingContext bindingContext, Binder<T> binder) {
        this.bindingContext = bindingContext;
        this.binder = binder;
    }

    /**
     * Returns the {@link BindingContext} associated with this group.
     *
     * @return the context passed at construction; never {@code null}
     */
    public BindingContext context() {
        return bindingContext;
    }

    /**
     * Bind {@code resource} as the exclusively active resource of type {@code T}.
     *
     * <p>If {@code resource} is already bound (reference equality via {@link Objects#equals}),
     * a new {@link BoundResource} handle is returned without issuing a redundant GL call.
     * Otherwise the previous resource is implicitly displaced by calling {@link Binder#bind}.</p>
     *
     * @param resource the resource to bind; must not be {@code null}
     * @return a handle that releases the binding when {@link BoundResource#unbind()} is called,
     *         or {@code null} if the binder threw a {@link BindingException}
     */
    public BoundResource<T> bind(T resource) {
        Objects.requireNonNull(resource, "Resource must not be null");
        try {
            lock.lock();
            // is this the same resource as the currently bound one?
            if(Objects.equals(boundResource, resource)) {
                return new BoundResource<>(this, resource);
            }
            // bind the new resource
            binder.bind(resource);
            this.boundResource = resource;
            if (LOG.isTraceEnabled()) {
                LOG.trace("Bound exclusive resource type {}: {}", binder.resourceType().getSimpleName(), resource);
            }
            return new BoundResource<>(this, resource);
        }
        catch(BindingException e) {
            return null;
        }
    }

    /**
     * Release the binding for {@code resource}, restoring the GL target to its unbound state.
     *
     * <p>After this call, another resource of the same type may be bound without interference.
     * The {@link Binder#unbind} implementation is responsible for issuing the GL call (e.g.
     * {@code glBindBuffer(target, 0)}).</p>
     *
     * @param resource the resource to unbind; must currently be bound
     * @throws BindingException if the underlying GL unbind call fails
     */
    public void unbind(T resource) throws BindingException {
        try {
            lock.lock();
            boundResource = binder.unbind(resource);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unbound exclusive resource type {}: {}", binder.resourceType().getSimpleName(), resource);
            }
        }
        finally {
            lock.unlock();
        }
    }
}
