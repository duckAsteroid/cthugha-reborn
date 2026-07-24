package com.asteroid.duck.opengl.util.toggle;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.RenderedItem;

import java.io.IOException;

public class ToggledRenderItem implements RenderedItem {
	private final Toggle enabler;
	private final RenderedItem target;

	public ToggledRenderItem(Toggle enabler, RenderedItem target) {
		this.enabler = enabler;
		this.target = target;
	}

	@Override
	public void init(RenderContext ctx) throws IOException {
		enabler.init(ctx);
		target.init(ctx);
	}

	@Override
	public void doRender(RenderContext ctx) {
		if (enabler.isRenderEnabled(ctx)) {
			target.doRender(ctx);
		}
	}

	@Override
	public void dispose() {
		target.dispose();
	}
}
