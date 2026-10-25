package com.asteroid.duck.opengl.util;

import com.asteroid.duck.opengl.util.resources.manager.ResourceListManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * A {@link RenderedItem} composed of an ordered list of child items, each of which is initialised
 * and rendered in sequence.
 *
 * <p>{@link #init} and {@link #doRender} simply iterate the child list and delegate. Disposal is
 * also delegated: each child's {@link RenderedItem#dispose()} is called when this composite is
 * disposed. Children are tracked by the parent {@link ResourceListManager} so they are all
 * cleaned up even if {@link #dispose()} is the only cleanup call made.</p>
 *
 * <p>Subclasses can add children after construction via {@link #addItems} and query them via
 * {@link #items()}. The insertion order determines the render order, which matters when compositing
 * (e.g. background before foreground, or opaque before transparent).</p>
 */
public class CompositeRenderItem extends ResourceListManager<RenderedItem> implements RenderedItem {
	private static final Logger LOG = LoggerFactory.getLogger(CompositeRenderItem.class);

	/** Create an empty composite. Children can be added later via {@link #addItems}. */
	public CompositeRenderItem() {
        super();
	}

	/**
	 * Create a composite pre-populated with the given items.
	 *
	 * @param items the child renderers, in the order they should be initialised and rendered
	 */
	public CompositeRenderItem(RenderedItem ... items) {
		super(Arrays.asList(items));
	}

	/**
	 * Create a composite pre-populated from a list.
	 *
	 * @param items the child renderers, in the order they should be initialised and rendered
	 */
	public CompositeRenderItem(List<RenderedItem> items) {
		super(items);
	}

    /**
     * Append one or more child items to this composite.
     * Intended for use by subclasses that add children during or after construction.
     *
     * @param items the items to append; rendered after any previously added children
     */
    protected void addItems(RenderedItem... items) {
        for(RenderedItem item : items) {
            add(item);
        }
    }

    /**
     * Return the live list of child items.
     * Subclasses can use this to inspect or reorder children at runtime.
     *
     * @return the mutable child list; changes are reflected immediately in subsequent renders
     */
    protected List<RenderedItem> items() {
        return resources;
    }

	@Override
	public void init(final RenderContext ctx) throws IOException {
		stream().forEach(item -> {
			try {
				item.init(ctx);
			} catch (IOException e) {
				LOG.error("Error initialising item", e);
			}
		});
	}

	@Override
	public void doRender(final RenderContext ctx) {
		stream().forEach(item -> item.doRender(ctx));
	}

	@Override
	public void dispose() {
		stream().forEach(RenderedItem::dispose);
	}
}
