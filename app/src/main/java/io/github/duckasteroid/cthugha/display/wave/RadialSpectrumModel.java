package io.github.duckasteroid.cthugha.display.wave;

import io.github.duckasteroid.cthugha.params.ColorParam;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.RenderMode;
import io.github.duckasteroid.cthugha.params.transform.TransformParams;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.EnumParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;
import org.joml.Vector4f;

import java.util.Arrays;

public class RadialSpectrumModel extends ParamNode {
    public BooleanParameter enabled = new BooleanParameter("enabled", true);
    public EnumParameter<RenderMode> mode = new EnumParameter<>("mode", Arrays.asList(RenderMode.values()), RenderMode.BUFFER);
    public IntegerParameter repeats = new IntegerParameter("repeats", 1, 8, 1);

    /** Palette index (0-1, normalised) at the inward tip of the filled shape -- used in BUFFER/BOTH. */
    public DoubleParameter innerColor = new DoubleParameter("innerColor", 0.0, 1.0, 1.0);
    /** RGB colour at the inward tip of the filled shape -- used in OVERLAY/BOTH. */
    public ColorParam innerColorRgb = new ColorParam("innerColorRgb");
    /** Palette index (0-1, normalised) at the base circle -- used in BUFFER/BOTH. */
    public DoubleParameter baseColor = new DoubleParameter("baseColor", 0.0, 1.0, 1.0);
    /** RGB colour at the base circle -- used in OVERLAY/BOTH. */
    public ColorParam baseColorRgb = new ColorParam("baseColorRgb");
    /** Palette index (0-1, normalised) at the outward tip of the filled shape -- used in BUFFER/BOTH. */
    public DoubleParameter outerColor = new DoubleParameter("outerColor", 0.0, 1.0, 1.0);
    /** RGB colour at the outward tip of the filled shape -- used in OVERLAY/BOTH. */
    public ColorParam outerColorRgb = new ColorParam("outerColorRgb");
    /** Palette index (0-1, normalised) used for the peak-hold line -- used in BUFFER/BOTH. */
    public DoubleParameter peakColor = new DoubleParameter("peakColor", 0.0, 1.0, 1.0);
    /** RGB colour used for the peak-hold line -- used in OVERLAY/BOTH. */
    public ColorParam peakColorRgb = new ColorParam("peakColorRgb");
    /** Shows/hides the filled spectrum shape by setting its colour alpha to 1.0/0.0. */
    public BooleanParameter showBars = new BooleanParameter("showBars", true);
    /** Shows/hides the peak-hold line by setting the peak colour alpha to 1.0/0.0. */
    public BooleanParameter showPeakTicks = new BooleanParameter("showPeakTicks", true);

    public TransformParams transform = new TransformParams("transform");

    /** Default name, used by the fixed single-instance construction path. */
    public static final String DEFAULT_NAME = "RadialSpectrum";

    public RadialSpectrumModel() {
        this(DEFAULT_NAME);
    }

    /**
     * Creates an instance with an explicit name, so {@link io.github.duckasteroid.cthugha.display.wave.WaveSystem}
     * can mount multiple independently-configured radial spectrum analysers (auto-named
     * "RadialSpectrum 1", "RadialSpectrum 2", ...) as siblings.
     */
    public RadialSpectrumModel(String name) {
        super(name);
        initFields(getClass());
        withUiHint(UiHint.ICON, "chart-pie");
        withResetAction();

        enabled.withDescription("Draws the frequency spectrum as bars radiating around a circle into the render buffer.");
        mode.withDescription("How this wave is rendered: baked into the indexed render buffer (default -- " +
                "subject to blur/translate like the rest of the visualisation), a crisp screen-space overlay " +
                "immune to those effects, or both at once.");
        repeats.withDescription("Number of times the full spectrum is tiled around the circle. Each successive tile alternates direction so bass and treble meet at the seams, giving rotational symmetry (2 = mirrored halves, 4 = four symmetric quadrants).");
        innerColor.withDescription("Palette index (as a fraction of the palette size) at the inward tip of the " +
                "filled shape, used when Mode is BUFFER or BOTH.");
        innerColor.withVisibleWhen("mode", RenderMode.BUFFER.name(), RenderMode.BOTH.name());
        innerColorRgb.withDescription("RGB colour at the inward tip of the filled shape, used when Mode is OVERLAY or BOTH.");
        innerColorRgb.withColorControl().withVisibleWhen("mode", RenderMode.OVERLAY.name(), RenderMode.BOTH.name());
        baseColor.withDescription("Palette index (as a fraction of the palette size) at the base circle -- the " +
                "midpoint of the fill gradient, used when Mode is BUFFER or BOTH.");
        baseColor.withVisibleWhen("mode", RenderMode.BUFFER.name(), RenderMode.BOTH.name());
        baseColorRgb.withDescription("RGB colour at the base circle -- the midpoint of the fill gradient, used when Mode is OVERLAY or BOTH.");
        baseColorRgb.withColorControl().withVisibleWhen("mode", RenderMode.OVERLAY.name(), RenderMode.BOTH.name());
        outerColor.withDescription("Palette index (as a fraction of the palette size) at the outward tip of the " +
                "filled shape, used when Mode is BUFFER or BOTH.");
        outerColor.withVisibleWhen("mode", RenderMode.BUFFER.name(), RenderMode.BOTH.name());
        outerColorRgb.withDescription("RGB colour at the outward tip of the filled shape, used when Mode is OVERLAY or BOTH.");
        outerColorRgb.withColorControl().withVisibleWhen("mode", RenderMode.OVERLAY.name(), RenderMode.BOTH.name());
        peakColor.withDescription("Palette index (as a fraction of the palette size) used for the smooth " +
                "peak-hold line, used when Mode is BUFFER or BOTH.");
        peakColor.withVisibleWhen("mode", RenderMode.BUFFER.name(), RenderMode.BOTH.name());
        peakColorRgb.withDescription("RGB colour used for the smooth peak-hold line, used when Mode is OVERLAY or BOTH.");
        peakColorRgb.withColorControl().withVisibleWhen("mode", RenderMode.OVERLAY.name(), RenderMode.BOTH.name());
        showBars.withDescription("Shows or hides the filled spectrum shape (sets its colour alpha to 1.0/0.0).");
        showPeakTicks.withDescription("Shows or hides the peak-hold line (sets the peak colour alpha to 1.0/0.0).");
        transform.withDescription("Position, scale, rotation and shear applied to the radial spectrum.");
    }

    /** Indexed (BUFFER) RGBA colour for the inner-tip fill gradient stop -- red channel only, alpha driven by {@link #showBars}. */
    public Vector4f innerColorVec() {
        return new Vector4f((float) innerColor.value, 0f, 0f, showBars.value ? 1f : 0f);
    }

    /** Indexed (BUFFER) RGBA colour for the base-circle fill gradient stop -- red channel only, alpha driven by {@link #showBars}. */
    public Vector4f baseColorVec() {
        return new Vector4f((float) baseColor.value, 0f, 0f, showBars.value ? 1f : 0f);
    }

    /** Indexed (BUFFER) RGBA colour for the outer-tip fill gradient stop -- red channel only, alpha driven by {@link #showBars}. */
    public Vector4f outerColorVec() {
        return new Vector4f((float) outerColor.value, 0f, 0f, showBars.value ? 1f : 0f);
    }

    /** Indexed (BUFFER) RGBA colour for the peak-hold line -- red channel only, alpha driven by {@link #showPeakTicks}. */
    public Vector4f peakColorVec() {
        return new Vector4f((float) peakColor.value, 0f, 0f, showPeakTicks.value ? 1f : 0f);
    }

    /** Overlay (OVERLAY) RGBA colour for the inner-tip fill gradient stop, alpha driven by {@link #showBars}. */
    public Vector4f innerColorVecRgb() {
        return innerColorRgb.toVector4f(showBars.value ? 1f : 0f);
    }

    /** Overlay (OVERLAY) RGBA colour for the base-circle fill gradient stop, alpha driven by {@link #showBars}. */
    public Vector4f baseColorVecRgb() {
        return baseColorRgb.toVector4f(showBars.value ? 1f : 0f);
    }

    /** Overlay (OVERLAY) RGBA colour for the outer-tip fill gradient stop, alpha driven by {@link #showBars}. */
    public Vector4f outerColorVecRgb() {
        return outerColorRgb.toVector4f(showBars.value ? 1f : 0f);
    }

    /** Overlay (OVERLAY) RGBA colour for the peak-hold line, alpha driven by {@link #showPeakTicks}. */
    public Vector4f peakColorVecRgb() {
        return peakColorRgb.toVector4f(showPeakTicks.value ? 1f : 0f);
    }
}
