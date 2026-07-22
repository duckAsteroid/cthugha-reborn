package io.github.duckasteroid.cthugha.display.wave;

import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.transform.TransformParams;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.EnumParameter;

import java.util.Arrays;

public class RadialWaveModel extends ParamNode {

    /**
     * Which stereo channel(s) to visualise. Ordinal order matches render-core's
     * {@code RadialWave.CHANNEL_*} constants ({@code BLEND=0, LEFT=1, RIGHT=2}) so it can be
     * passed straight to {@code RadialWave.setChannelMode(int)} via {@code ordinal()}.
     * Unlike {@code AudioWave}, render-core's {@code RadialWave} has no combined two-line stereo
     * draw mode, so there is no STEREO option here (see linear {@link OscilloscopeModel.ChannelMode}).
     */
    public enum ChannelMode { BLEND, LEFT, RIGHT }

    public BooleanParameter enabled = new BooleanParameter("enabled", false);
    public DoubleParameter amplitude = new DoubleParameter("amplitude", 0.1, 10.0, 0.2);
    public DoubleParameter lineWidth = new DoubleParameter("lineWidth", 0.5, 10.0, 2.0);
    public BooleanParameter ellipse = new BooleanParameter("ellipse", false);
    public EnumParameter<ChannelMode> channelMode = new EnumParameter<>("channelMode", Arrays.asList(ChannelMode.values()));
    public TransformParams transform = new TransformParams("transform");

    /** Default name, used by the fixed single-instance construction path. */
    public static final String DEFAULT_NAME = "RadialWave";

    public RadialWaveModel() {
        this(DEFAULT_NAME);
    }

    /**
     * Creates an instance with an explicit name, so {@link io.github.duckasteroid.cthugha.display.wave.WaveSystem}
     * can mount multiple independently-configured radial waves (auto-named "RadialWave 1",
     * "RadialWave 2", ...) as siblings.
     */
    public RadialWaveModel(String name) {
        super(name);
        initFields(getClass());
        withUiHint(UiHint.ICON, "radio");
        withResetAction();

        amplitude.withDescription("Radial scale of the waveform trace.");
        lineWidth.withDescription("Thickness of the drawn line, in pixels.");
        ellipse.withDescription("Bend the trace around an ellipse instead of a circle.");
        channelMode.withDescription("Which stereo channel(s) to draw: BLEND averages left+right into one " +
                "trace (default), LEFT/RIGHT draw a single channel. render-core's radial waveform has no " +
                "combined stereo draw mode, unlike the linear oscilloscope.");
    }
}
