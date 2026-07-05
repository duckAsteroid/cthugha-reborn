package io.github.duckasteroid.cthugha.params.transform;

import io.github.duckasteroid.cthugha.params.ParamNode;

import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import org.joml.Vector2f;

import java.util.function.DoublePredicate;
import java.util.stream.Stream;

/**
 * A pair of bounded {@code double} parameters representing a 2-D point or vector.
 *
 * <p>Both the {@code x} and {@code y} components share the same min/max range.  Typical uses
 * include scale factors, shear amounts, translation offsets, and rotation centre points.</p>
 *
 * <p>Values are stored normalised in {@code [0, 1]} by convention; callers multiply against
 * screen dimensions to obtain pixel coordinates.</p>
 */
public class XYParam extends ParamNode {

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

  public XYParam(String name, double min, double max, double x, double y) {
    super(name);
    this.x = new DoubleParameter("X", min, max, x);
    this.y = new DoubleParameter("Y", min, max, y);
    initChildren(this.x, this.y);
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

  /** Returns the current x/y values as a JOML {@link Vector2f}. */
  public Vector2f toVector2f() {
    return new Vector2f((float) x.value, (float) y.value);
  }
}
