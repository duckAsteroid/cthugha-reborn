package com.asteroid.duck.opengl.util.toggle;

import com.asteroid.duck.opengl.util.RenderContext;

/**
 * An instance of this determines when {@link ToggledRenderItem} renders.
 */
public interface Toggle {
	/**
	 * Initialise any state the toggle needs from the render context.
	 * Called once before the first {@link #isRenderEnabled} call; the default is a no-op.
	 *
	 * @param ctx the render context for the current display loop
	 */
	default void init(RenderContext ctx) {}

	/**
	 * Called from render method of ToggledRenderItem - to decide if it should render
	 * @param ctx the context of the render
	 * @return true to render, false otherwise
	 */
	boolean isRenderEnabled(RenderContext ctx);
}
