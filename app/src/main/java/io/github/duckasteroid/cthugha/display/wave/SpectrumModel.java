package io.github.duckasteroid.cthugha.display.wave;

import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.transform.TransformParams;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;

public class SpectrumModel extends ParamNode {
    public BooleanParameter enabled = new BooleanParameter("enabled", false);
    public TransformParams transform = new TransformParams("transform");

    public SpectrumModel() {
        super("Spectrum");
        initFields(getClass());
        withUiHint(UiHint.ICON, "bar-chart-2");
        withResetAction();
    }
}
