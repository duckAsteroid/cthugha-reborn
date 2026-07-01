package io.github.duckasteroid.cthugha.params;

/** Hint keys and values for the remote-control UI, stored in {@link Node#getUiHints()}. */
public final class UiHint {

    // ---- hint keys ----

    /** Key: which widget type to render for this node. */
    public static final String CONTROL_TYPE = "control-type";

    /** Key: when {@code "true"}, the remote UI should not render this node's children inline. */
    public static final String SKIP_CHILDREN = "skip-children";

    /**
     * Key: a Lucide icon name (kebab-case, e.g. {@code "music"}, {@code "volume-2"}, {@code "palette"})
     * that the remote UI will render alongside the control label.
     */
    public static final String ICON = "icon";

    // ---- control-type values ----

    /** Horizontal slider with numeric readout. Default for numeric params. */
    public static final String SLIDER = "SLIDER";

    /** Rotary knob with numeric readout. */
    public static final String KNOB = "KNOB";

    /** Carousel strip with prev/next arrows and optional image preview per option. */
    public static final String CAROUSEL = "CAROUSEL";

    private UiHint() {}
}
