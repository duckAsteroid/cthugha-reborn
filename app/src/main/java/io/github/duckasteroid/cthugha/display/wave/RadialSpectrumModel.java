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
        withUiHint(UiHint.ICON, "chart-pie");
        withResetAction();

        enabled.withDescription("Draws the frequency spectrum as bars radiating around a circle into the render buffer.");
        repeats.withDescription("Number of times the full spectrum is tiled around the circle. Each successive tile alternates direction so bass and treble meet at the seams, giving rotational symmetry (2 = mirrored halves, 4 = four symmetric quadrants).");
        transform.withDescription("Position, scale, rotation and shear applied to the radial spectrum.");
    }
}
