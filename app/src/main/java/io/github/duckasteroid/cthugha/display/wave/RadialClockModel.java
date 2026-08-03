package io.github.duckasteroid.cthugha.display.wave;

import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.transform.TransformParams;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.EnumParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;
import org.joml.Vector4f;

import java.util.Arrays;

public class RadialClockModel extends ParamNode {
    public BooleanParameter enabled = new BooleanParameter("enabled", true);
    public EnumParameter<RadialClockAnalyser.GrowthMode> growthMode =
            new EnumParameter<>("growthMode", Arrays.asList(RadialClockAnalyser.GrowthMode.values()));
    public IntegerParameter repeats = new IntegerParameter("repeats", 1, 8, 1);
    /** Fraction of the maximum non-overlapping capsule width to actually draw, leaving a gap between ticks. */
    public DoubleParameter widthFraction = new DoubleParameter("widthFraction", 0.05, 1.0, RadialClockAnalyser.DEFAULT_WIDTH_FRACTION);
    /** Base ring radius (dot position), in NDC units. */
    public DoubleParameter baseRadius = new DoubleParameter("baseRadius", 0.05, 1.0, RadialClockAnalyser.DEFAULT_BASE_RADIUS);
    /** Maximum outward extension at full magnitude, in NDC units. */
    public DoubleParameter outerHeight = new DoubleParameter("outerHeight", 0.0, 1.0, RadialClockAnalyser.DEFAULT_OUTER_HEIGHT);
    /** Maximum inward contraction at full magnitude, in NDC units. */
    public DoubleParameter innerDepth = new DoubleParameter("innerDepth", 0.0, 1.0, RadialClockAnalyser.DEFAULT_INNER_DEPTH);

    /** Palette index (0-1, normalised) at the deepest possible inward extent. */
    public DoubleParameter innerColor = new DoubleParameter("innerColor", 0.0, 1.0, 1.0);
    /** Palette index (0-1, normalised) at the resting ring radius. */
    public DoubleParameter baseColor = new DoubleParameter("baseColor", 0.0, 1.0, 1.0);
    /** Palette index (0-1, normalised) at the furthest possible outward extent. */
    public DoubleParameter outerColor = new DoubleParameter("outerColor", 0.0, 1.0, 1.0);

    public TransformParams transform = new TransformParams("transform");

    /** Default name, used by the fixed single-instance construction path. */
    public static final String DEFAULT_NAME = "RadialClock";

    public RadialClockModel() {
        this(DEFAULT_NAME);
    }

    /**
     * Creates an instance with an explicit name, so {@link io.github.duckasteroid.cthugha.display.wave.WaveSystem}
     * can mount multiple independently-configured radial clock analysers (auto-named
     * "RadialClock 1", "RadialClock 2", ...) as siblings.
     */
    public RadialClockModel(String name) {
        super(name);
        initFields(getClass());
        withUiHint(UiHint.ICON, "clock");
        withResetAction();

        enabled.withDescription("Draws the frequency spectrum as retro clock-face ticks -- a resting dot per " +
                "frequency bin that stretches into a radial bar as that bin's magnitude rises. Unlike the other " +
                "spectrum analysers, this has no peak-hold indicator and applies no smoothing.");
        growthMode.withDescription("Direction each tick grows from the resting ring as its magnitude rises: " +
                "OUTWARD (away from centre), INWARD (toward centre), or BOTH (away from the ring in both directions).");
        repeats.withDescription("Number of times the full set of bins is tiled around the circle. Odd-numbered " +
                "tiles mirror their bin order so bass and treble meet at every seam, giving rotational symmetry.");
        widthFraction.withDescription("Fraction of the maximum non-overlapping tick width to actually draw, " +
                "leaving a gap between adjacent ticks. 1.0 means ticks just touch at their most extreme radius.");
        baseRadius.withDescription("Radius of the resting ring -- where each tick's dot sits at zero magnitude.");
        outerHeight.withDescription("Maximum outward extension at full magnitude (used by OUTWARD and BOTH).");
        innerDepth.withDescription("Maximum inward contraction at full magnitude (used by INWARD and BOTH).");
        innerColor.withDescription("Palette index (as a fraction of the palette size) at the deepest possible " +
                "inward extent. This is a palette-indexed render buffer, so only the index (red channel) matters " +
                "-- not a full RGB colour.");
        baseColor.withDescription("Palette index (as a fraction of the palette size) at the resting ring radius.");
        outerColor.withDescription("Palette index (as a fraction of the palette size) at the furthest possible outward extent.");
        transform.withDescription("Position, scale, rotation and shear applied to the radial clock.");
    }

    /** RGBA colour for the inner-extent gradient stop -- red channel only. */
    public Vector4f innerColorVec() {
        return new Vector4f((float) innerColor.value, 0f, 0f, 1f);
    }

    /** RGBA colour for the resting-ring gradient stop -- red channel only. */
    public Vector4f baseColorVec() {
        return new Vector4f((float) baseColor.value, 0f, 0f, 1f);
    }

    /** RGBA colour for the outer-extent gradient stop -- red channel only. */
    public Vector4f outerColorVec() {
        return new Vector4f((float) outerColor.value, 0f, 0f, 1f);
    }
}
