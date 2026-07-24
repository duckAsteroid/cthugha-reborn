package com.asteroid.duck.opengl.util.resources.buffer;

import com.asteroid.duck.opengl.util.GLCoded;

import static org.lwjgl.opengl.GL15.*;

/**
 * Usage hint passed to {@code glBufferData} describing expected update/read frequency.
 */
public enum UpdateHint implements GLCoded {
    /**
     * The data store contents will be modified once and used many times.
     */
    STATIC(GL_STATIC_DRAW),
    /**
     * The data store contents will be modified once and used at most a few times.
     */
    STREAM(GL_STREAM_DRAW),
    /**
     * The data store contents will be modified repeatedly and used many times
     */
    DYNAMIC(GL_DYNAMIC_DRAW);

    final int glCode;

    UpdateHint(int glCode) {
        this.glCode = glCode;
    }

    /**
     * Returns the underlying OpenGL usage constant.
     *
     * @return GL usage enum (for example {@code GL_DYNAMIC_DRAW})
     */
    public int openGlCode() {
        return glCode;
    }
}
