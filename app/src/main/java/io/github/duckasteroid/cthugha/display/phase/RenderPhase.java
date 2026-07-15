package io.github.duckasteroid.cthugha.display.phase;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import io.github.duckasteroid.cthugha.params.ParamNode;

import java.io.IOException;

/**
 * A self-contained rendering component that participates in up to two render phases per frame:
 *
 * <ol>
 *   <li>{@link #indexedRender} — called while {@code renderFBO} is bound; writes palette indices
 *       directly into the R16 indexed buffer (waves, flash effects, in-buffer text). The red
 *       channel value {@code index/paletteSize} encodes the palette entry; PaletteRenderer
 *       resolves it as {@code pixelIndex = sampledValue * totalEntries}.</li>
 *   <li>{@link #screenRender} — called after palette conversion to the screen FBO; renders RGBA
 *       overlays on top of the palette-converted image (text, QR code).</li>
 * </ol>
 *
 * All methods are no-ops by default so implementations only override what they participate in.
 * {@link #registerActions} lets each phase attach its own entries to the param tree.
 */
public interface RenderPhase {

    default void init(RenderContext ctx) throws IOException {}

    /** Called with renderFBO bound; renders directly into the R16 indexed buffer. */
    default void indexedRender(RenderContext ctx) throws IOException {}

    /** Called after palette display; renders RGBA overlays onto the screen FBO. */
    default void screenRender(RenderContext ctx) throws IOException {}

    default void dispose() {}

    /** Optionally attach param-tree actions to the General group. */
    default void registerActions(ParamNode generalGroup, RenderActionQueue renderActions) {}
}
