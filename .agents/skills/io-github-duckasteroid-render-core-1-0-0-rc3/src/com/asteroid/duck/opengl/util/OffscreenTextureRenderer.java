package com.asteroid.duck.opengl.util;

import com.asteroid.duck.opengl.util.resources.framebuffer.FrameBuffer;
import com.asteroid.duck.opengl.util.resources.texture.Texture;

import java.awt.Rectangle;
import java.io.IOException;

import static org.lwjgl.opengl.GL11.glViewport;

/**
 * Wraps another {@link RenderedItem} and renders it to an offscreen {@link Texture} using a {@link FrameBuffer}.
 */
public class OffscreenTextureRenderer implements RenderedItem {
	// wrap another rendered item and render it to texture using an FBO
	private final RenderedItem renderedItem;
	private final FrameBuffer  fbo;

	/**
	 * Create an offscreen renderer that draws {@code renderedItem} into {@code targetTexture}.
	 *
	 * @param renderedItem  the render item whose output should go to the offscreen buffer
	 * @param targetTexture the texture to render into; must already have been allocated
	 *                      with the desired dimensions before {@link #init} is called
	 */
	public OffscreenTextureRenderer(RenderedItem renderedItem, Texture targetTexture) {
		this.renderedItem = renderedItem;
		this.fbo = new FrameBuffer(targetTexture);
	}

	@Override
	public void init(RenderContext ctx) throws IOException {
		renderedItem.init(ctx);
	}

	@Override
	public void doRender(RenderContext ctx) {
		fbo.bind();
		renderedItem.doRender(ctx);
		fbo.unbind();
		Rectangle w = ctx.getWindow();
		glViewport(0, 0, w.width, w.height);
	}

	@Override
	public void dispose() {
		renderedItem.dispose();
		fbo.dispose();
	}
}
