package io.github.duckasteroid.cthugha.display.wave;

import io.github.duckasteroid.cthugha.params.ColorParam;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.RenderMode;
import io.github.duckasteroid.cthugha.params.transform.TransformParams;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.EnumParameter;
import org.joml.Vector4f;

import java.util.Arrays;

public class SpectrumModel extends ParamNode {

    /**
     * Which screen edge the linear bar row is anchored to. Used by {@code WavePhase} as a base
     * transform matrix that {@link #transform} composes on top of -- BOTTOM (default) is a no-op
     * matching the existing bars-grow-up-from-the-bottom layout, TOP flips the growth direction
     * vertically, and LEFT/RIGHT rotate the whole bar row 90 degrees so bars run vertically along
     * that edge instead. Radial spectrum has no equivalent (repeats/rotation already covers it).
     */
    public enum Position { BOTTOM, TOP, LEFT, RIGHT }

    public BooleanParameter enabled = new BooleanParameter("enabled", true);
    public EnumParameter<RenderMode> mode = new EnumParameter<>("mode", Arrays.asList(RenderMode.values()), RenderMode.BUFFER);
    public EnumParameter<Position> position = new EnumParameter<>("position", Arrays.asList(Position.values()));

    /** Palette index (0-1, normalised) at the base of each bar -- used in BUFFER/BOTH. */
    public DoubleParameter barColorLow = new DoubleParameter("barColorLow", 0.0, 1.0, 1.0);
    /** RGB colour at the base of each bar -- used in OVERLAY/BOTH. */
    public ColorParam barColorLowRgb = new ColorParam("barColorLowRgb");
    /** Palette index (0-1, normalised) at the tip of each bar -- used in BUFFER/BOTH. */
    public DoubleParameter barColorHigh = new DoubleParameter("barColorHigh", 0.0, 1.0, 1.0);
    /** RGB colour at the tip of each bar -- used in OVERLAY/BOTH. */
    public ColorParam barColorHighRgb = new ColorParam("barColorHighRgb");
    /** Palette index (0-1, normalised) used for the peak-hold tick marks -- used in BUFFER/BOTH. */
    public DoubleParameter peakColor = new DoubleParameter("peakColor", 0.0, 1.0, 1.0);
    /** RGB colour used for the peak-hold tick marks -- used in OVERLAY/BOTH. */
    public ColorParam peakColorRgb = new ColorParam("peakColorRgb");
    /** Shows/hides the bars by setting their colour alpha to 1.0/0.0. */
    public BooleanParameter showBars = new BooleanParameter("showBars", true);
    /** Shows/hides the peak-hold tick marks by setting the peak colour alpha to 1.0/0.0. */
    public BooleanParameter showPeakTicks = new BooleanParameter("showPeakTicks", true);

    public TransformParams transform = new TransformParams("transform");

    /** Default name, used by the fixed single-instance construction path. */
    public static final String DEFAULT_NAME = "Spectrum";

    public SpectrumModel() {
        this(DEFAULT_NAME);
    }

    /**
     * Creates an instance with an explicit name, so {@link io.github.duckasteroid.cthugha.display.wave.WaveSystem}
     * can mount multiple independently-configured spectrum analysers (auto-named "Spectrum 1",
     * "Spectrum 2", ...) as siblings.
     */
    public SpectrumModel(String name) {
        super(name);
        initFields(getClass());
        withUiHint(UiHint.ICON, "chart-column");
        withResetAction();

        enabled.withDescription("Draws the frequency spectrum as a row of bars into the render buffer.");
        mode.withDescription("How this wave is rendered: baked into the indexed render buffer (default -- " +
                "subject to blur/translate like the rest of the visualisation), a crisp screen-space overlay " +
                "immune to those effects, or both at once.");
        position.withDescription("Which screen edge the bar row is anchored to. Applied as a base transform " +
                "underneath Transform below, so Transform still shapes the bars in their own local space " +
                "before this preset repositions the whole row.");
        barColorLow.withDescription("Palette index (as a fraction of the palette size) at the base of each " +
                "bar, used when Mode is BUFFER or BOTH.");
        barColorLow.withVisibleWhen("mode", RenderMode.BUFFER.name(), RenderMode.BOTH.name());
        barColorLowRgb.withDescription("RGB colour at the base of each bar, used when Mode is OVERLAY or BOTH.");
        barColorLowRgb.withColorControl().withVisibleWhen("mode", RenderMode.OVERLAY.name(), RenderMode.BOTH.name());
        barColorHigh.withDescription("Palette index (as a fraction of the palette size) at the tip of each " +
                "bar, used when Mode is BUFFER or BOTH.");
        barColorHigh.withVisibleWhen("mode", RenderMode.BUFFER.name(), RenderMode.BOTH.name());
        barColorHighRgb.withDescription("RGB colour at the tip of each bar, used when Mode is OVERLAY or BOTH.");
        barColorHighRgb.withColorControl().withVisibleWhen("mode", RenderMode.OVERLAY.name(), RenderMode.BOTH.name());
        peakColor.withDescription("Palette index (as a fraction of the palette size) used for the peak-hold " +
                "tick marks drawn above each bar, used when Mode is BUFFER or BOTH.");
        peakColor.withVisibleWhen("mode", RenderMode.BUFFER.name(), RenderMode.BOTH.name());
        peakColorRgb.withDescription("RGB colour used for the peak-hold tick marks, used when Mode is OVERLAY or BOTH.");
        peakColorRgb.withColorControl().withVisibleWhen("mode", RenderMode.OVERLAY.name(), RenderMode.BOTH.name());
        showBars.withDescription("Shows or hides the bars (sets their colour alpha to 1.0/0.0).");
        showPeakTicks.withDescription("Shows or hides the peak-hold tick marks (sets the peak colour alpha to 1.0/0.0).");
        transform.withDescription("Position, scale, rotation and shear applied to the spectrum bars, on top of the Position preset above.");
    }

    /** Indexed (BUFFER) RGBA colour for the bar-base gradient stop -- red channel only, alpha driven by {@link #showBars}. */
    public Vector4f barColorLowVec() {
        return new Vector4f((float) barColorLow.value, 0f, 0f, showBars.value ? 1f : 0f);
    }

    /** Indexed (BUFFER) RGBA colour for the bar-tip gradient stop -- red channel only, alpha driven by {@link #showBars}. */
    public Vector4f barColorHighVec() {
        return new Vector4f((float) barColorHigh.value, 0f, 0f, showBars.value ? 1f : 0f);
    }

    /** Indexed (BUFFER) RGBA colour for the peak-hold tick marks -- red channel only, alpha driven by {@link #showPeakTicks}. */
    public Vector4f peakColorVec() {
        return new Vector4f((float) peakColor.value, 0f, 0f, showPeakTicks.value ? 1f : 0f);
    }

    /** Overlay (OVERLAY) RGBA colour for the bar-base gradient stop, alpha driven by {@link #showBars}. */
    public Vector4f barColorLowVecRgb() {
        return barColorLowRgb.toVector4f(showBars.value ? 1f : 0f);
    }

    /** Overlay (OVERLAY) RGBA colour for the bar-tip gradient stop, alpha driven by {@link #showBars}. */
    public Vector4f barColorHighVecRgb() {
        return barColorHighRgb.toVector4f(showBars.value ? 1f : 0f);
    }

    /** Overlay (OVERLAY) RGBA colour for the peak-hold tick marks, alpha driven by {@link #showPeakTicks}. */
    public Vector4f peakColorVecRgb() {
        return peakColorRgb.toVector4f(showPeakTicks.value ? 1f : 0f);
    }
}
