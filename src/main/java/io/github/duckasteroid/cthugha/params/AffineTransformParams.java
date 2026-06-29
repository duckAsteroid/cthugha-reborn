package io.github.duckasteroid.cthugha.params;

import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.DoublePredicate;

/**
 * A composite parameter node that exposes the components of a 2-D affine transform as
 * individually tunable parameters.
 *
 * <p>The transform is built from four child parameter groups, applied in this order by
 * {@link #applyTo(Dimension, AffineTransform)}:</p>
 * <ol>
 *   <li>{@link #scale} – x/y scale factors (identity = 1).</li>
 *   <li>{@link #translate} – x/y translation offsets (identity = 0).</li>
 *   <li>{@link #shear} – x/y shear factors (identity = 0).</li>
 *   <li>{@link #rotate} – rotation angle in radians, around {@link #rotateCenter}.</li>
 * </ol>
 *
 * <p>Each component is skipped when it equals its identity value (checked to 10 decimal
 * places), avoiding unnecessary matrix multiplications.</p>
 */
public class AffineTransformParams extends AbstractNode {
  public static final double MIN = Double.MIN_VALUE;
  public static final double MAX = Double.MAX_VALUE;

  /** X and Y scale factors; identity value is 1.0 for both components. */
  public final XYParam scale = new XYParam("Scale",MIN, MAX, 1.0);

  /** X and Y shear factors; identity value is 0.0 for both components. */
  public final XYParam shear = new XYParam("Shear", MIN, MAX, 0.0);

  /** X and Y translation offsets in pixels; identity value is 0.0. */
  public final XYParam translate = new XYParam("Translation", MIN, MAX, 0.0);

  /** Rotation angle in radians, in the range {@code [0, 2π]}. */
  public final DoubleParameter rotate = new DoubleParameter("Rotation in radians", 0, 2 * Math.PI);

  /** Centre point for rotation, expressed as a normalised {@code [0, 1]} fraction of the render surface. */
  public final XYParam rotateCenter = new XYParam("Rotation center point", 0, 1, 0);

  /**
   * @param name display name for this transform parameter group
   */
  public AffineTransformParams(String name) {
    super(name);
    initChildren(scale, shear, translate, rotate, rotateCenter);
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
   * Applies the non-identity components of this parameter group to {@code transform} and
   * returns it.  The supplied transform is mutated in place.
   *
   * @param dim       render surface dimensions, used to resolve {@link #rotateCenter} to pixels
   * @param transform the transform to modify
   * @return the same {@code transform} instance, after applying the active components
   */
  public AffineTransform applyTo(Dimension dim, AffineTransform transform) {
    if (!scale.is(UNITY)) {
      transform.scale(scale.x.value, scale.y.value);
    }
    if (!translate.is(ZERO)) {
      transform.translate(translate.x.value, translate.y.value);
    }
    if (!shear.is(ZERO)) {
      transform.shear(shear.x.value, shear.y.value);
    }
    if (rotate.value % 2 * Math.PI != 0.0) {
      Point rotateCenterPoint = rotateCenter.pixelLocation(dim);
      transform.rotate(rotate.value, rotateCenterPoint.x, rotateCenterPoint.y);
    }
    return transform;
  }

}
