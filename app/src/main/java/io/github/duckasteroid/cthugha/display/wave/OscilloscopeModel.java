package io.github.duckasteroid.cthugha.display.wave;

import io.github.duckasteroid.cthugha.params.ColorParam;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.RenderMode;
import io.github.duckasteroid.cthugha.params.transform.TransformParams;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.EnumParameter;

import java.util.Arrays;

public class OscilloscopeModel extends ParamNode {

    /**
     * Which stereo channel(s) to visualise. Ordinal order matches render-core's
     * {@code AudioWave.CHANNEL_*} constants ({@code BLEND=0, LEFT=1, RIGHT=2, STEREO=3}) so it
     * can be passed straight to {@code AudioWave.setChannelMode(int)} via {@code ordinal()}.
     */
    public enum ChannelMode { BLEND, LEFT, RIGHT, STEREO }

    public BooleanParameter enabled = new BooleanParameter("enabled", true);
    public EnumParameter<RenderMode> mode = new EnumParameter<>("mode", Arrays.asList(RenderMode.values()), RenderMode.BUFFER);
    /** Palette index (0-1, normalised) of the trace colour -- used in BUFFER/BOTH. */
    public DoubleParameter index = new DoubleParameter("index", 0.0, 1.0, 1.0);
    /** RGB colour of the trace -- used in OVERLAY/BOTH. */
    public ColorParam color = new ColorParam("color");
    public DoubleParameter amplitude = new DoubleParameter("amplitude", 0.01, 5.0, 1.0);
    public DoubleParameter lineWidth = new DoubleParameter("lineWidth", 0.5, 10.0, 2.0);
    public BooleanParameter ellipse = new BooleanParameter("ellipse", false);
    public EnumParameter<ChannelMode> channelMode = new EnumParameter<>("channelMode", Arrays.asList(ChannelMode.values()));
    public TransformParams transform = new TransformParams("transform");

    /** Default name, used by the fixed single-instance construction path. */
    public static final String DEFAULT_NAME = "Oscilloscope";

    public OscilloscopeModel() {
        this(DEFAULT_NAME);
    }

    /**
     * Creates an instance with an explicit name, so {@link io.github.duckasteroid.cthugha.display.wave.WaveSystem}
     * can mount multiple independently-configured oscilloscopes (auto-named "Oscilloscope 1",
     * "Oscilloscope 2", ...) as siblings.
     */
    public OscilloscopeModel(String name) {
        super(name);
        initFields(getClass());
        withUiHint(UiHint.ICON, "activity");
        withResetAction();

        mode.withDescription("How this wave is rendered: baked into the indexed render buffer (default -- " +
                "subject to blur/translate like the rest of the visualisation), a crisp screen-space overlay " +
                "immune to those effects, or both at once.");
        index.withDescription("Palette index (as a fraction of the palette size) of the trace colour, used when Mode is BUFFER or BOTH.");
        index.withVisibleWhen("mode", RenderMode.BUFFER.name(), RenderMode.BOTH.name());
        color.withDescription("RGB colour of the trace, used when Mode is OVERLAY or BOTH.");
        color.withColorControl().withVisibleWhen("mode", RenderMode.OVERLAY.name(), RenderMode.BOTH.name());
        amplitude.withDescription("Vertical scale of the waveform trace.");
        lineWidth.withDescription("Thickness of the drawn line, in pixels.");
        ellipse.withDescription("Bend the trace around an ellipse instead of drawing it as a straight horizontal line.");
        channelMode.withDescription("Which stereo channel(s) to draw: BLEND averages left+right into one " +
                "trace (default), LEFT/RIGHT draw a single channel, STEREO draws both simultaneously (left " +
                "above centre, right below).");
    }
}
