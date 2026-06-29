package io.github.duckasteroid.cthugha.display.wave;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.TransformParams;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;

public class RadialWaveModel extends AbstractNode {
    public BooleanParameter enabled = new BooleanParameter("enabled", false);
    public DoubleParameter amplitude = new DoubleParameter("amplitude", 0.1, 10.0, 1.0);
    public BooleanParameter ellipse = new BooleanParameter("ellipse", false);
    public TransformParams transform = new TransformParams("transform");

    public RadialWaveModel() {
        super("RadialWave");
        initFields(getClass());
    }
}
