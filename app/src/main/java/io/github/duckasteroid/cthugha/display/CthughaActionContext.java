package io.github.duckasteroid.cthugha.display;

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

    public CthughaActionContext(JCthugha cthugha, Random rng) {
        this.cthugha = cthugha;
        this.rng = rng;
    }

    @Override public TabStore tabStore()          { return cthugha.tabStore; }
    @Override public GeneratorRegistry registry() { return cthugha.translateSource; }
    @Override public TabBuffer currentBuffer()    { return cthugha.getTabBuffer(); }
    @Override public Dimension resolution()       { return new Dimension(cthugha.bufferWidth, cthugha.bufferHeight); }
    @Override public Random rng()                 { return rng; }
    @Override public void notify(String message)  { cthugha.notify(message); }
    @Override public void loadTabBuffer(TabBuffer buf) { cthugha.loadTabBuffer(buf); }
    @Override public PaletteMap currentPalette()       { return cthugha.paletteMap; }
    @Override public void loadPalette(PaletteMap map)  { cthugha.loadPalette(map); }
}
