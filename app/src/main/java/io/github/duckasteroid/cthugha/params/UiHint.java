package io.github.duckasteroid.cthugha.params;

/** Hint keys and values for the remote-control UI, stored in {@link Node#getUiHints()}. */
public final class UiHint {

    // ---- hint keys ----

    /** Key: which widget type to render for this node. */
    public static final String CONTROL_TYPE = "control-type";

    /** Key: when {@code "true"}, the remote UI should not render this node's children inline. */
    public static final String SKIP_CHILDREN = "skip-children";

    /**
     * Key: when {@code "true"}, the remote UI omits this node entirely.
     * Useful for actions that exist only for keyboard-binding lookups.
     */
    public static final String HIDDEN = "hidden";

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

    /** Thumbnail grid, one tile per option; tapping a tile selects it immediately. */
    public static final String GRID = "GRID";

    /** Searchable, scrollable text list; tapping a row selects it immediately. */
    public static final String LIST = "LIST";

    /**
     * Value for {@link #CONTROL_TYPE} on a {@code ContainerNode}: renders its direct
     * container children as a horizontal tab strip.  Any child container that has
     * {@code control-type=EXPANDER} is excluded from the tab strip and rendered below
     * as a collapsible section instead.
     */
    public static final String TABS = "TABS";

    /**
     * Value for {@link #CONTROL_TYPE} on a {@code ContainerNode} that is a child of a
     * {@link #TABS} container: the node is excluded from the tab strip and rendered
     * below the tabs as a collapsible expander instead.
     */
    public static final String EXPANDER = "EXPANDER";

    // ---- scale values (used with CONTROL_TYPE = SLIDER or KNOB) ----

    /**
     * Key: how the slider maps its position to the parameter value.
     * Absent → linear. Use {@link #SCALE_LOG} for logarithmic / power-curve mapping.
     */
    public static final String SCALE = "scale";

    /**
     * Value for {@link #SCALE}: applies a power-curve ({@code 1-(1-t)^3}) so that
     * the upper end of the slider has finer resolution than the lower end.
     * Useful for multipliers that are perceptually sensitive near 1.0.
     */
    public static final String SCALE_LOG = "log";

    /**
     * Value for {@link #CONTROL_TYPE} on a {@code StringNode}: renders a resizable
     * multi-line code editor.  Submit with Ctrl+Enter.  Compile errors are returned
     * in the PATCH response as {@code "compileError"}.
     */
    public static final String CODE_EDITOR = "CODE_EDITOR";

    /**
     * Value for {@link #CONTROL_TYPE} on an {@code XYParam} container: renders its
     * {@code X}/{@code Y} children as a single draggable point on a 2-D pad (a rectangle
     * shaped like the render buffer, with a crosshair marking the current position) instead
     * of two separate sliders. See
     * {@link io.github.duckasteroid.cthugha.params.transform.XYParam#withPadControl()}.
     */
    public static final String XY_PAD = "XY_PAD";

    /**
     * Value for {@link #CONTROL_TYPE} on a {@code StringNode}: renders a searchable picker over
     * every {@code Action} node currently in the tree, instead of a free-text field. Used to
     * reference an existing action (e.g. by an {@code ActionTrigger}) without hand-typing a path.
     */
    public static final String ACTION_PICKER = "ACTION_PICKER";

    private UiHint() {}
}
