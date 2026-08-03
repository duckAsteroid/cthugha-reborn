package com.asteroid.duck.opengl.util.renderaction;

import com.asteroid.duck.opengl.util.RenderContext;

/**
 * Interface to some action that can be performed during rendering.
 */
public interface Action {
    /**
     * Perform the render action in the given context.
     * @param context the render context
     */
    void onRender(RenderContext context);
}
