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
        withUiHint(UiHint.ICON, "chart-column");
        withResetAction();

        enabled.withDescription("Draws the frequency spectrum as a row of bars into the render buffer.");
        transform.withDescription("Position, scale, rotation and shear applied to the spectrum bars.");
    }
}
