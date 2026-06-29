package io.github.duckasteroid.cthugha.params.values;

import io.github.duckasteroid.cthugha.params.NodeType;
import io.github.duckasteroid.cthugha.params.AbstractValue;

/**
 * A bounded {@code int} parameter.
 *
 * <p>{@link #setValue(Number)} converts its argument to {@code int} by rounding via
 * {@link Math#round(double)}, so this parameter can be driven by the animation system's
 * {@code double}-valued outputs without loss of intent.</p>
 */
public class IntegerParameter extends AbstractValue {

  private final int min;
  private final int max;

  /** Current value; lies in {@code [min, max]}. */
  public int value;

  /**
   * Creates a parameter with initial value equal to {@code min}.
   *
   * @param description display name
   * @param min         lower bound (inclusive)
   * @param max         upper bound (inclusive)
   */
  public IntegerParameter(String description, int min, int max) {
    super(description);
    this.min = min;
    this.max = max;
  }

  /**
   * @param description display name
   * @param min         lower bound (inclusive)
   * @param max         upper bound (inclusive)
   * @param value       initial value
   */
  public IntegerParameter(String description, int min, int max, int value) {
    this(description, min, max);
    this.value = value;
  }

  @Override
  public NodeType getNodeType() {
    return NodeType.INTEGER;
  }

  public Number getValue() {
    return value;
  }

  public void setValue(Number value) {
    this.value = (int)Math.round(value.doubleValue());
  }

  public Number getMin() {
    return min;
  }

  public Number getMax() {
    return max;
  }

}
