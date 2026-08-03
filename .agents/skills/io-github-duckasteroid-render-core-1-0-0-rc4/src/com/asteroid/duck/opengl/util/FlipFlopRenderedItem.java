package com.asteroid.duck.opengl.util;

import java.io.IOException;

/**
 * A render item that has two children A and B - it flip flops rendering one or the other.
 */
public class FlipFlopRenderedItem implements RenderedItem {
	private final RenderedItem itemA;
	private final RenderedItem itemB;
	private boolean a_active = true;

	/**
	 * Create a flip-flop that alternates between two child render items on consecutive frames.
	 *
	 * @param itemA the render item used on even frames (the first frame rendered)
	 * @param itemB the render item used on odd frames
	 */
	public FlipFlopRenderedItem(RenderedItem itemA, RenderedItem itemB) {
		this.itemA = itemA;
		this.itemB = itemB;
	}

	@Override
	public void init(RenderContext ctx) throws IOException {
		itemA.init(ctx);
		itemB.init(ctx);
	}

	@Override
	public void doRender(RenderContext ctx) {
        if (a_active) {
          itemA.doRender(ctx);
        } else {
          itemB.doRender(ctx);
        }
		a_active = !a_active;
	}

	@Override
	public void dispose() {
		itemA.dispose();
        itemB.dispose();
	}
}
