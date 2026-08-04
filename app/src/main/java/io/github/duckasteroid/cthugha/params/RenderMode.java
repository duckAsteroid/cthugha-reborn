package io.github.duckasteroid.cthugha.params;

/**
 * Where a render-phase element (quote text, a wave renderer, ...) draws itself: as a screen-space
 * overlay on the final RGBA framebuffer (crisp, immune to blur/translate), baked into the
 * palette-indexed render buffer (subject to blur/translate like the rest of the visualisation),
 * or both at once.
 */
public enum RenderMode {
    OVERLAY, BUFFER, BOTH
}
