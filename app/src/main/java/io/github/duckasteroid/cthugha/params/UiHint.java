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
     * Value for {@link #CONTROL_TYPE} on a {@code ContainerNode} whose children are a dynamic
     * list of item containers (e.g. {@code WaveSystem}'s wave instances) plus exactly one picker
     * leaf and one create {@link io.github.duckasteroid.cthugha.params.action.Action} used to add
     * more. The remote UI renders the existing items first and keeps the picker hidden behind an
     * "add" button below them, revealing it only once that button is clicked, rather than showing
     * it permanently above the list.
     */
    public static final String ADD_LIST = "ADD_LIST";

    /**
     * Value for {@link #CONTROL_TYPE} on a top-level tab {@code ContainerNode} whose content is a
     * single "active item" container (e.g. {@code GeneratorRegistry}'s currently-selected
     * {@code TabGenerator}) alongside a {@code Save Name} leaf and {@code Save} action that apply
     * to it. The remote UI renders {@code Save Name}/{@code Save} nested inside the active item's
     * own expander instead of as siblings above/below it — display only: their param-tree paths
     * are unchanged, since {@code cthugha.ini} key bindings (e.g. {@code SHIFT+S}) reference the
     * {@code Save} action's real, fixed path and would break if it moved with the active item.
     */
    public static final String GENERATOR_TAB = "GENERATOR_TAB";

    /**
     * Key: on a {@code ContainerNode}, names a sibling {@code ENUM} leaf (by node name, not
     * full path) whose currently-selected option's {@code preview} image should be shown as a
     * static thumbnail alongside this container's own header — e.g. {@code VideoPhase}'s
     * "Playback" group points at the "Video" picker so the currently-loaded video stays visible
     * without switching to the picker itself. Resolved entirely client-side from data already in
     * the tree (the sibling's live value plus its own {@code options[].preview}), so no separate
     * preview field or SSE event is needed — the sibling leaf already updates live.
     */
    public static final String PREVIEW_OF = "preview-of";

    /**
     * Key: on a {@code ContainerNode} that also carries {@link #PREVIEW_OF}, names a direct
     * {@code BOOLEAN} child (by node name, not full path) that pauses/resumes playback of the
     * previewed item — e.g. {@code VideoPhase}'s "Playback" group points at its own "Paused"
     * child. The remote UI renders that child as a play/pause icon overlaid on the preview
     * thumbnail (bottom-right corner, showing the action a tap would perform) instead of as its
     * own toggle row; the child should also carry {@link #HIDDEN} so it doesn't additionally
     * render as a row, while remaining a normal addressable/serialized leaf for the overlay to
     * read and PATCH.
     */
    public static final String PAUSE_CONTROL = "pause-control";

    private UiHint() {}
}
