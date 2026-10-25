package io.github.duckasteroid.cthugha.params.transform;

import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.UiHint;

import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;

/**
 * Optional perspective projection parameters.
 *
 * <p>When {@link #enabled} is true, {@link TransformParams#applyTo} applies a symmetric
 * perspective frustum followed by a view-distance translation before the affine components.
 * This maps flat 2-D NDC geometry into a 3-D perspective view. {@link #turn} and {@link #tip}
 * (and their spins) rotate the geometry in depth inside that view; when one is non-zero, or a
 * 3-D animation package runs, the projection is used even while {@link #enabled} is off, at the
 * distance that shows the {@code z = 0} plane at its normal size.</p>
 */
public class PerspectiveParams extends ParamNode {

    public BooleanParameter enabled = new BooleanParameter("enabled", false);

    /** Vertical field of view in degrees. */
    public DoubleParameter fovY = new DoubleParameter("fovY (degrees)", 10, 170, 60);

    /** How far behind the camera to place the geometry (positive = into the screen). */
    public DoubleParameter zDistance = new DoubleParameter("zDistance", 0.1, 10.0, 1.0);

    /** Rotation about the vertical axis, in degrees (a coin turning on its edge). */
    public DoubleParameter turn = new DoubleParameter("Turn", 0, 360, 0);

    /** Rotation about the horizontal axis, in degrees (a page flipping). */
    public DoubleParameter tip = new DoubleParameter("Tip", 0, 360, 0);

    /** Continuous Turn, in revolutions per second. */
    public DoubleParameter turnSpin = new DoubleParameter("Turn Spin", -5, 5, 0);

    /** Continuous Tip, in revolutions per second. */
    public DoubleParameter tipSpin = new DoubleParameter("Tip Spin", -5, 5, 0);

    public PerspectiveParams() {
        super("Perspective");
        initFields(getClass());
        withUiHint(UiHint.ICON, "eye");

        enabled.withDescription("Turns on the 3-D perspective projection. When off, the "
            + "enclosing transform stays purely 2-D affine.");
        fovY.withDescription("Vertical field of view of the perspective projection, in degrees. "
            + "Wider angles exaggerate depth distortion.");
        zDistance.withDescription("Distance the geometry is pushed away from the camera after "
            + "projection is applied, while enabled. Larger values shrink the apparent perspective "
            + "effect.");
        turn.withDescription("Rotation in depth about the vertical axis, in degrees: the element "
            + "turns like a coin on its edge. Non-zero switches the projection on at the distance "
            + "that keeps the element its normal size.");
        tip.withDescription("Rotation in depth about the horizontal axis, in degrees: the element "
            + "tips like a page flipping. Non-zero switches the projection on.");
        turnSpin.withDescription("Continuous Turn in revolutions per second; negative reverses.");
        tipSpin.withDescription("Continuous Tip in revolutions per second; negative reverses.");
    }
}
