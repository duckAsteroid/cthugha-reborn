package io.github.duckasteroid.cthugha.display.wave;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.transform.TransformParams;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;

public class OscilloscopeModel extends AbstractNode {
    public BooleanParameter enabled = new BooleanParameter("enabled", true);
    public DoubleParameter amplitude = new DoubleParameter("amplitude", 0.5, 50.0, 0.2);
    public BooleanParameter ellipse = new BooleanParameter("ellipse", false);
    public TransformParams transform = new TransformParams("transform");

    public OscilloscopeModel() {
        super("Oscilloscope");
        initFields(getClass());
        withUiHint(UiHint.ICON, "activity");
        withResetAction();
    }
}
