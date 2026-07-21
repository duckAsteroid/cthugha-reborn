package io.github.duckasteroid.cthugha.map;

import io.github.duckasteroid.cthugha.params.action.ActionContext;

public interface PaletteActionContext extends ActionContext {
    PaletteMap currentPalette();
    void loadPalette(PaletteMap map);
}
