package io.github.duckasteroid.cthugha.params;

import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import java.awt.Dimension;
import java.awt.Point;
import java.util.Collection;
import java.util.List;
import java.util.function.DoublePredicate;
import java.util.stream.Stream;

/**
 * A pair of bounded {@code double} parameters representing a 2-D point or vector.
 *
 * <p>Both the {@code x} and {@code y} components share the same min/max range.  Typical uses
 * include scale factors, shear amounts, translation offsets, and rotation centre points.</p>
 *
 * <p>{@link #pixelLocation(Dimension)} converts the normalised {@code [0, 1]} values to
 * absolute pixel coordinates by multiplying against the supplied screen dimensions, which is
 * convenient for specifying anchor points in a resolution-independent way.</p>
 */
public class XYParam extends AbstractNode {

  /** Horizontal component. */
  public final DoubleParameter x;

  /** Vertical component. */
  public final DoubleParameter y;

  /**
   * Creates an XY parameter with range {@code [0, 1]} and initial value {@code 0}.
   *
   * @param name display name
   */
  public XYParam(String name) {
    this(name, 0, 1, 0);
  }

  /**
   * Creates an XY parameter with explicit range and initial value (both components share the
   * same bounds and starting value).
   *
   * @param name  display name
   * @param min   minimum value for both x and y
   * @param max   maximum value for both x and y
   * @param value initial value for both x and y
   */
  public XYParam(String name, double min, double max, double value) {
    super(name);
    this.x = new DoubleParameter("X", min, max, value);
    this.y = new DoubleParameter("Y", min, max, value);
    initChildren(x, y);
  }

  /**
   * Returns {@code true} if either the {@code x} or {@code y} value satisfies {@code test}.
   *
   * @param test predicate to evaluate against each component
   */
  public boolean is(DoublePredicate test) {
    return Stream.of(x, y)
      .map(DoubleParameter::getValue)
      .mapToDouble(Number::doubleValue)
      .anyMatch(test);
  }

  /**
   * Sets both components to the centre of {@code dims} in pixels.
   *
   * @param dims the screen or component dimensions
   */
  public void setCenterOf(Dimension dims) {
    x.setValue(dims.width / 2);
    y.setValue(dims.height / 2);
  }

  /**
   * Converts this XY parameter to an absolute pixel position by multiplying the normalised
   * {@code x}/{@code y} values by the width and height of {@code d} respectively.
   *
   * @param d the reference dimensions (typically the screen or render surface)
   * @return the corresponding pixel-space {@link Point}
   */
  public Point pixelLocation(Dimension d) {
    return new Point((int) (d.width * x.value), (int) (d.height * y.value));
  }
}
