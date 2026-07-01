package io.github.duckasteroid.cthugha.params;

/** Hint to the remote-control UI about which widget to use for a numeric parameter. */
public enum UiHint {
    /** Horizontal slider with numeric readout. Default for most numeric params. */
    SLIDER,
    /** Rotary knob with numeric readout. Suitable for amplitude, frequency, pan, etc. */
    KNOB,
    /** Carousel strip with prev/next arrows and optional image preview per option. */
    CAROUSEL
}
