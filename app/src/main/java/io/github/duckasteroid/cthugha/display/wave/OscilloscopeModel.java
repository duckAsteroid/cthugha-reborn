package io.github.duckasteroid.cthugha.display.wave;

import io.github.duckasteroid.cthugha.params.ParamNode;
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
    public DoubleParameter amplitude = new DoubleParameter("amplitude", 0.5, 50.0, 0.2);
    public DoubleParameter lineWidth = new DoubleParameter("lineWidth", 0.5, 10.0, 2.0);
    public BooleanParameter ellipse = new BooleanParameter("ellipse", false);
    public EnumParameter<ChannelMode> channelMode = new EnumParameter<>("channelMode", Arrays.asList(ChannelMode.values()));
    public TransformParams transform = new TransformParams("transform");

    public OscilloscopeModel() {
        super("Oscilloscope");
        initFields(getClass());
        withUiHint(UiHint.ICON, "activity");
        withResetAction();

        amplitude.withDescription("Vertical scale of the waveform trace.");
        lineWidth.withDescription("Thickness of the drawn line, in pixels.");
        ellipse.withDescription("Bend the trace around an ellipse instead of drawing it as a straight horizontal line.");
        channelMode.withDescription("Which stereo channel(s) to draw: BLEND averages left+right into one " +
                "trace (default), LEFT/RIGHT draw a single channel, STEREO draws both simultaneously (left " +
                "above centre, right below).");
    }
}
