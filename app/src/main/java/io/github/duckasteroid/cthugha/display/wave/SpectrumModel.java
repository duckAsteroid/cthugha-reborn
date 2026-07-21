package io.github.duckasteroid.cthugha.display.wave;

import io.github.duckasteroid.cthugha.params.ParamNode;
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

    public BooleanParameter enabled = new BooleanParameter("enabled", false);
    public EnumParameter<Position> position = new EnumParameter<>("position", Arrays.asList(Position.values()));

    /** Palette index (0-1, normalised) at the base of each bar. */
    public DoubleParameter barColorLow = new DoubleParameter("barColorLow", 0.0, 1.0, 1.0);
    /** Palette index (0-1, normalised) at the tip of each bar. */
    public DoubleParameter barColorHigh = new DoubleParameter("barColorHigh", 0.0, 1.0, 1.0);
    /** Palette index (0-1, normalised) used for the peak-hold tick marks. */
    public DoubleParameter peakColor = new DoubleParameter("peakColor", 0.0, 1.0, 1.0);
    /** Shows/hides the bars by setting their colour alpha to 1.0/0.0. */
    public BooleanParameter showBars = new BooleanParameter("showBars", true);
    /** Shows/hides the peak-hold tick marks by setting the peak colour alpha to 1.0/0.0. */
    public BooleanParameter showPeakTicks = new BooleanParameter("showPeakTicks", true);

    public TransformParams transform = new TransformParams("transform");

    public SpectrumModel() {
        super("Spectrum");
        initFields(getClass());
        withUiHint(UiHint.ICON, "chart-column");
        withResetAction();

        enabled.withDescription("Draws the frequency spectrum as a row of bars into the render buffer.");
        position.withDescription("Which screen edge the bar row is anchored to. Applied as a base transform " +
                "underneath Transform below, so Transform still shapes the bars in their own local space " +
                "before this preset repositions the whole row.");
        barColorLow.withDescription("Palette index (as a fraction of the palette size) at the base of each " +
                "bar. This is a palette-indexed render buffer, so only the index (red channel) matters -- " +
                "not a full RGB colour.");
        barColorHigh.withDescription("Palette index (as a fraction of the palette size) at the tip of each bar.");
        peakColor.withDescription("Palette index (as a fraction of the palette size) used for the peak-hold " +
                "tick marks drawn above each bar.");
        showBars.withDescription("Shows or hides the bars (sets their colour alpha to 1.0/0.0).");
        showPeakTicks.withDescription("Shows or hides the peak-hold tick marks (sets the peak colour alpha to 1.0/0.0).");
        transform.withDescription("Position, scale, rotation and shear applied to the spectrum bars, on top of the Position preset above.");
    }

    /** RGBA colour for the bar-base gradient stop -- red channel only, alpha driven by {@link #showBars}. */
    public Vector4f barColorLowVec() {
        return new Vector4f((float) barColorLow.value, 0f, 0f, showBars.value ? 1f : 0f);
    }

    /** RGBA colour for the bar-tip gradient stop -- red channel only, alpha driven by {@link #showBars}. */
    public Vector4f barColorHighVec() {
        return new Vector4f((float) barColorHigh.value, 0f, 0f, showBars.value ? 1f : 0f);
    }

    /** RGBA colour for the peak-hold tick marks -- red channel only, alpha driven by {@link #showPeakTicks}. */
    public Vector4f peakColorVec() {
        return new Vector4f((float) peakColor.value, 0f, 0f, showPeakTicks.value ? 1f : 0f);
    }
}
