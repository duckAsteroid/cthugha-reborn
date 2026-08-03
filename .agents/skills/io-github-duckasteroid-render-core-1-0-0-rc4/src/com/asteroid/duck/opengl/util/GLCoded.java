package com.asteroid.duck.opengl.util;

import java.util.function.Supplier;

/**
 * An interface that represents an OpenGL object that has an integer code.
 */
public interface GLCoded extends Supplier<Integer> {
    /**
     * Returns the OpenGL code associated with this object.
     *
     * @return the OpenGL code
     */
    int openGlCode();

    @Override
    default Integer get() {
        return openGlCode();
    }
}
