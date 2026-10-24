package com.asteroid.duck.opengl.util;

import com.asteroid.duck.opengl.util.resources.Resource;

import java.io.IOException;

/**
 * The interface to some resource that gets a chance to:
 * <ol>
 * 	<li>Initialise before rendering starts</li>
 * 	<li>Render during the main render loop</li>
 * </ol>
 */
public interface RenderedItem extends Resource {
    /**
     * One-time setup called before the first render. Allocate all GL resources here (VAOs, VBOs,
     * shader programs, textures) via the {@link RenderContext}. This method is always called on
     * the GL thread.
     *
     * @param ctx the render context providing access to the resource manager, window geometry, and
     *            other shared services
     * @throws IOException if shader sources or texture files cannot be read
     */
	void init(RenderContext ctx) throws IOException;

    /**
     * Render one frame. Called on every iteration of the render loop, on the GL thread.
     * All per-frame GL state changes (uniform updates, draw calls) belong here. Do not
     * allocate new GL handles inside this method; use {@link #init} for that.
     *
     * @param ctx the render context; provides the current {@link com.asteroid.duck.opengl.util.timer.Clock},
     *            window dimensions, and other per-frame state
     */
	void doRender(RenderContext ctx);
}
