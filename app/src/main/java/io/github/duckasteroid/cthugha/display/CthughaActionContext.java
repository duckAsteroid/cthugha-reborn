package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import io.github.duckasteroid.cthugha.JCthugha;
import io.github.duckasteroid.cthugha.map.PaletteActionContext;
import io.github.duckasteroid.cthugha.map.PaletteMap;
import io.github.duckasteroid.cthugha.tab.*;

import java.awt.Dimension;
import java.util.Random;

/**
 * Concrete {@link TabActionContext} backed by a live {@link JCthugha} instance.
 * Created once in {@link CthughaWindow#init()} and shared with the remote server
 * so that both keyboard bindings and remote API calls execute actions in the same context.
 */
public class CthughaActionContext implements TabActionContext, PaletteActionContext {

    private final JCthugha cthugha;
    private final Random rng;
    private final RenderActionQueue renderActions;
    private final Runnable rebuildTranslateMap;

    public CthughaActionContext(JCthugha cthugha, Random rng, RenderActionQueue renderActions,
                                 Runnable rebuildTranslateMap) {
        this.cthugha = cthugha;
        this.rng = rng;
        this.renderActions = renderActions;
        this.rebuildTranslateMap = rebuildTranslateMap;
    }

    @Override public TabStore tabStore()          { return cthugha.tabStore; }
    @Override public GeneratorRegistry registry() { return cthugha.translateSource; }
    @Override public TabBuffer currentBuffer()    { return cthugha.getTabBuffer(); }
    @Override public Dimension resolution()       { return new Dimension(cthugha.bufferWidth, cthugha.bufferHeight); }
    @Override public Random rng()                 { return rng; }
    @Override public void notify(String message)  { cthugha.notify(message); }
    @Override public void loadTabBuffer(TabBuffer buf) { cthugha.loadTabBuffer(buf); }
    @Override public void rebuildTranslateMap() {
        renderActions.enqueue("rebuildTranslateMap", rc -> rebuildTranslateMap.run());
    }
    @Override public PaletteMap currentPalette()       { return cthugha.paletteMap; }
    @Override public void loadPalette(PaletteMap map)  { cthugha.loadPalette(map); }
}
