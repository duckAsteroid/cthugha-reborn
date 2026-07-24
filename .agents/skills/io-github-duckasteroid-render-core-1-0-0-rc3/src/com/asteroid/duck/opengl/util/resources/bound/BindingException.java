package com.asteroid.duck.opengl.util.resources.bound;

/**
 * Thrown when a {@link Binder} fails to bind or unbind a GL resource.
 *
 * <p>Wraps the underlying cause (typically an OpenGL error or an attempt to double-bind) so
 * callers can distinguish binding failures from other {@link RuntimeException}s without catching
 * a broad exception type.</p>
 */
public class BindingException extends Exception {

    /**
     * Create a binding exception with a descriptive message.
     *
     * @param message explanation of what failed, including the resource type and operation
     */
    public BindingException(String message) {
        super(message);
    }

    /**
     * Create a binding exception wrapping an underlying cause.
     *
     * @param message explanation of what failed
     * @param cause   the original exception that triggered the binding failure
     */
    public BindingException(String message, Throwable cause) {
        super(message, cause);
    }
}
