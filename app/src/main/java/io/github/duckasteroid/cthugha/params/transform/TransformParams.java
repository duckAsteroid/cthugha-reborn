package io.github.duckasteroid.cthugha.params.transform;

import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.UiHint;

import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import org.joml.Matrix4f;

import java.util.function.DoublePredicate;

/**
 * A composite parameter node that exposes the components of a 2-D transform as
 * individually tunable parameters.
 *
 * <p>The transform is built from five child parameter groups, applied in this order by
 * {@link #applyTo(Matrix4f)}:</p>
 * <ol>
 *   <li>{@link #perspective} – optional perspective projection (disabled by default).</li>
 *   <li>{@link #scale} – x/y scale factors (identity = 1).</li>
 *   <li>{@link #translate} – x/y translation offsets in NDC units (identity = 0).</li>
 *   <li>{@link #shear} – x/y shear factors (identity = 0).</li>
 *   <li>{@link #rotate} – rotation angle in radians around {@link #rotateCenter} in NDC space.</li>
 * </ol>
 *
 * <p>Each affine component is skipped when it equals its identity value (checked to 10 decimal
 * places), avoiding unnecessary matrix multiplications.</p>
 */
public class TransformParams extends ParamNode {

  /** Optional perspective projection applied before the affine components. */
  public final PerspectiveParams perspective = new PerspectiveParams();

  /** X and Y scale factors; identity value is 1.0 for both components. */
  public final XYParam scale = new XYParam("Scale", 0.05, 10.0, 1.0);

  /** X and Y shear factors; identity value is 0.0 for both components. */
  public final XYParam shear = new XYParam("Shear", -3.0, 3.0, 0.0);

  /** X and Y translation offsets in NDC units; identity value is 0.0. */
  public final XYParam translate = new XYParam("Translation", -2.0, 2.0, 0.0);

  /** Rotation angle in radians, in the range {@code [0, 2π]}. */
  public final DoubleParameter rotate = new DoubleParameter("Rotation in radians", 0, 2 * Math.PI);

  /** Centre point for rotation in NDC space (origin = screen centre). */
  public final XYParam rotateCenter = new XYParam("Rotation center point", -1, 1, 0).withPadControl();

  /**
   * @param name display name for this transform parameter group
   */
  public TransformParams(String name) {
    super(name);
    rotate.withUiHint(UiHint.ICON, "rotate-cw");
    initChildren(perspective, scale, shear, translate, rotate, rotateCenter);

    perspective.withDescription("Optional 3-D perspective projection applied before the affine "
        + "components below (scale, translate, shear, rotate).");
    scale.withDescription("X/Y scale factors applied to the geometry. 1.0 on both axes is "
        + "identity (no scaling).");
    shear.withDescription("X/Y shear factors applied to the geometry. 0.0 on both axes is "
        + "identity (no shear).");
    translate.withDescription("X/Y translation offset in NDC units. 0.0 on both axes is "
        + "identity (no offset).");
    rotate.withDescription("Rotation angle in radians, applied around Rotation Center Point.");
    rotateCenter.withDescription("Pivot point for the rotation, in NDC space where (0,0) is the "
        + "screen centre.");
  }

  /** Returns a predicate that is true when a double equals {@code expected} to {@code dp} decimal places. */
  private static DoublePredicate equals(final double expected, final int dp) {
    final double factor = Math.pow(10, dp);
    return (actual) -> Math.round(actual * factor) == Math.round(expected * factor);
  }

  /** Predicate that matches 1.0 to 10 decimal places; used to detect identity scale. */
  public final DoublePredicate UNITY = equals(1.0, 10);

  /** Predicate that matches 0.0 to 10 decimal places; used to detect identity translate/shear. */
  public final DoublePredicate ZERO = equals(0.0, 10);

  /**
   * Applies the active components of this parameter group to {@code matrix} and returns it.
   * The supplied matrix is mutated in place.  All coordinates are in NDC space.
   *
   * <p>If {@link PerspectiveParams#enabled} is true, a perspective projection followed by the
   * configured z-distance translation is applied first, making the subsequent affine components
   * act as model transforms within the 3-D view.</p>
   *
   * @param matrix the transform to modify
   * @return the same {@code matrix} instance, after applying the active components
   */
  public Matrix4f applyTo(Matrix4f matrix) {
    if (perspective.enabled.value) {
      float fovY = (float) Math.toRadians(perspective.fovY.value);
      float zDist = (float) perspective.zDistance.value;
      matrix.perspective(fovY, 1.0f, 0.1f, 10.0f).translate(0f, 0f, -zDist);
    }
    if (!scale.is(UNITY)) {
      matrix.scale((float) scale.x.value, (float) scale.y.value, 1f);
    }
    if (!translate.is(ZERO)) {
      matrix.translate((float) translate.x.value, (float) translate.y.value, 0f);
    }
    if (!shear.is(ZERO)) {
      float shx = (float) shear.x.value;
      float shy = (float) shear.y.value;
      // Shear: x' = x + shx*y,  y' = shy*x + y
      // Column-major Matrix4f: col0=(1,shy,0,0), col1=(shx,1,0,0), col2=(0,0,1,0), col3=(0,0,0,1)
      matrix.mul(new Matrix4f(
          1f, shy, 0f, 0f,
          shx, 1f, 0f, 0f,
          0f, 0f, 1f, 0f,
          0f, 0f, 0f, 1f
      ));
    }
    if (rotate.value % (2 * Math.PI) != 0.0) {
      float cx = (float) rotateCenter.x.value;
      float cy = (float) rotateCenter.y.value;
      float angle = (float) rotate.value;
      matrix.translate(cx, cy, 0f).rotateZ(angle).translate(-cx, -cy, 0f);
    }
    return matrix;
  }
}
