package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.params.action.ActionContext;

import java.awt.Dimension;

/**
 * Extended {@link ActionContext} that exposes the tab-persistence aspects of the
 * running application.  Actions defined in the {@code tab} package cast to this
 * interface to access the store, registry, and current buffer.
 */
public interface TabActionContext extends ActionContext {
    /** The persistent store for reading and writing saved tab presets. */
    TabStore tabStore();

    /** The registry of canonical {@link TabGenerator} instances. */
    GeneratorRegistry registry();

    /** The {@link TabBuffer} currently displayed on screen. */
    TabBuffer currentBuffer();

    /** The render resolution (determines {@code .tab} filename on save/load). */
    Dimension resolution();

    /** Replaces the active translation buffer with {@code buf} on the CPU side. */
    void loadTabBuffer(TabBuffer buf);

    /**
     * Queues a re-upload of the current translation buffer (see {@link #loadTabBuffer}) to the
     * GPU on the render thread. Must be called after {@link #loadTabBuffer} for the change to
     * become visible on screen.
     */
    void rebuildTranslateMap();
}
