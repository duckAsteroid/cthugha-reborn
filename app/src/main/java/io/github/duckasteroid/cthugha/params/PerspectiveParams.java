package io.github.duckasteroid.cthugha.params;

import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;

/**
 * Optional perspective projection parameters.
 *
 * <p>When {@link #enabled} is true, {@link TransformParams#applyTo} applies a symmetric
 * perspective frustum followed by a view-distance translation before the affine components.
 * This maps flat 2-D NDC geometry into a 3-D perspective view.</p>
 */
public class PerspectiveParams extends AbstractNode {

    public BooleanParameter enabled = new BooleanParameter("enabled", false);

    /** Vertical field of view in degrees. */
    public DoubleParameter fovY = new DoubleParameter("fovY (degrees)", 10, 170, 60);

    /** How far behind the camera to place the geometry (positive = into the screen). */
    public DoubleParameter zDistance = new DoubleParameter("zDistance", 0.1, 10.0, 1.0);

    public PerspectiveParams() {
        super("Perspective");
        initFields(getClass());
        withUiHint(UiHint.ICON, "eye");
    }
}
