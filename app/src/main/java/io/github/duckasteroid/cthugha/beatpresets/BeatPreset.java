package io.github.duckasteroid.cthugha.beatpresets;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;

/**
 * Persistent named snapshot of a {@link io.github.duckasteroid.cthugha.display.BeatDetectorSettingsNode}
 * subtree (band Hz ranges plus threshold/sensitivity/decay/history), captured via {@link
 * io.github.duckasteroid.cthugha.screenconfig.ScreenConfigParams#capture}.
 *
 * <p>Serialised as {@code <slug>.json} inside the beat-presets directory. Unlike a screen config,
 * the Beat Detector subtree has no {@code DynamicChildList} descendants, so there is no
 * equivalent of {@code dynamicChildren} to capture.</p>
 */
public class BeatPreset {
    public String name;
    /** Flat, order-preserving path→value map; paths are slash-delimited relative to the Beat Detector node. */
    public Map<String, Object> params;

    /** File name within the store directory — set by {@link BeatPresetStore} on load, never serialised. */
    @JsonIgnore
    public String fileName;
}
