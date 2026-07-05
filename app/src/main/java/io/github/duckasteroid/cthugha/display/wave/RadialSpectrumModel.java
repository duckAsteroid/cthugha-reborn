package io.github.duckasteroid.cthugha.display.wave;

import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.transform.TransformParams;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;

public class RadialSpectrumModel extends ParamNode {
    public BooleanParameter enabled = new BooleanParameter("enabled", false);
    public IntegerParameter repeats = new IntegerParameter("repeats", 1, 8, 1);
    public TransformParams transform = new TransformParams("transform");

    public RadialSpectrumModel() {
        super("RadialSpectrum");
        initFields(getClass());
        withUiHint(UiHint.ICON, "pie-chart");
        withResetAction();
    }
}
