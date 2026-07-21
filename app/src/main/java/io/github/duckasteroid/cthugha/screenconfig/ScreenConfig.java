package io.github.duckasteroid.cthugha.screenconfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Map;

/**
 * Persistent named snapshot of the whole parameter tree (waves, animations, active palette,
 * active tab generator and its params, blur, etc.) captured via {@link ScreenConfigParams}.
 *
 * <p>Serialised as {@code <slug>.json} inside the screen-configs directory. Unlike a saved tab
 * preset, no resolution-bound binary is stored alongside it: the active tab generator's own
 * params are captured like any other value and its translation map is regenerated on load at
 * whatever resolution is current, so a screen config is resolution-independent by construction.</p>
 */
public class ScreenConfig {
    public String name;
    /** Flat, order-preserving path→value map; paths are slash-delimited relative to the tree root. */
    public Map<String, Object> params;

    /** File name within the store directory — set by {@link ScreenConfigStore} on load, never serialised. */
    @JsonIgnore
    public String fileName;
}
