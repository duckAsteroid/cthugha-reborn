package io.github.duckasteroid.cthugha.display.wave;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.TransformParams;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;

public class SpectrumModel extends AbstractNode {
    public BooleanParameter enabled = new BooleanParameter("enabled", false);
    public TransformParams transform = new TransformParams("transform");

    public SpectrumModel() {
        super("Spectrum");
        initFields(getClass());
    }
}
