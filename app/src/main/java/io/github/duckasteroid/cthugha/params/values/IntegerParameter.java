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
  private final int defaultValue;

  /** Current value; lies in {@code [min, max]}. */
  public int value;

  /**
   * Creates a parameter with initial value {@code 0}.
   *
   * @param description display name
   * @param min         lower bound (inclusive)
   * @param max         upper bound (inclusive)
   */
  public IntegerParameter(String description, int min, int max) {
    this(description, min, max, 0);
  }

  /**
   * @param description display name
   * @param min         lower bound (inclusive)
   * @param max         upper bound (inclusive)
   * @param value       initial value (also used as the reset default)
   */
  public IntegerParameter(String description, int min, int max, int value) {
    super(description);
    this.min = min;
    this.max = max;
    this.value = value;
    this.defaultValue = value;
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
    fireChangeListeners();
  }

  public Number getMin() {
    return min;
  }

  public Number getMax() {
    return max;
  }

  @Override
  public void setNormalisedValue(double normalisedValue) {
    this.value = (int) Math.round(min + (max - min) * normalisedValue);
    fireChangeListeners();
  }

  @Override
  public void reset() {
    setValue(defaultValue);
  }

}
