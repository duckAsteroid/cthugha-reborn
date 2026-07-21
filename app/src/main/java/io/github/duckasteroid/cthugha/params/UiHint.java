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
     * Key: on a {@link #GRID} node, overrides how each option's {@code preview} image is
     * cropped/fit within its tile. Absent means the default square photo-thumbnail treatment
     * ({@code object-fit: cover} on a 1:1 tile), which crops wide images. See
     * {@link #PREVIEW_STYLE_SWATCH} for the alternative used by colour-swatch previews.
     */
    public static final String PREVIEW_STYLE = "preview-style";

    /**
     * Value for {@link #PREVIEW_STYLE}: renders each option's preview as a short, wide strip
     * with {@code object-fit: fill} instead of a cropped square — appropriate for a preview
     * image that already encodes all its information across its full width, such as a
     * palette's colour swatch (see {@code PaletteLibraryNode}), where cropping to a square
     * would hide most of the colours.
     */
    public static final String PREVIEW_STYLE_SWATCH = "SWATCH";

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
     * every {@code Action} and settable value leaf currently in the tree, instead of a free-text
     * field. Used to reference an existing node by path (e.g. by a {@code Binding}'s target)
     * without hand-typing it. When the picked target is a settable leaf (not an {@code Action}),
     * the remote UI also renders a typed/ranged control for the paired value field named by
     * {@link #PAIRED_VALUE_FIELD}, matching the target's own type/min/max/options; when it's an
     * {@code Action}, that control is hidden since there's nothing to set.
     */
    public static final String TARGET_PICKER = "TARGET_PICKER";

    /**
     * Key: on a {@link #TARGET_PICKER} node, the name of the sibling leaf (within the same
     * container) that holds the value to set when the picked target is not an {@code Action}.
     * The sibling itself is normally hidden ({@link #HIDDEN}) since the target picker renders it
     * inline with a control shaped to the target's type.
     */
    public static final String PAIRED_VALUE_FIELD = "paired-value-field";

    private UiHint() {}
}
