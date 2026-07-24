package com.asteroid.duck.opengl.util.events;

/**
 * Callback invoked when the framebuffer is resized.
 * Register instances via {@link com.asteroid.duck.opengl.util.RenderContext#addResizeListener}.
 */
public interface ResizeListener {
    /**
     * Called after the framebuffer (and viewport) have been updated to the new dimensions.
     *
     * @param width  the new framebuffer width in pixels
     * @param height the new framebuffer height in pixels
     */
    void onResize(int width, int height);
}
