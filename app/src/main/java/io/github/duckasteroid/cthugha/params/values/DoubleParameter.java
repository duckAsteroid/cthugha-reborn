package io.github.duckasteroid.cthugha.params.values;

import io.github.duckasteroid.cthugha.params.NodeType;
import io.github.duckasteroid.cthugha.params.AbstractValue;

/**
 * A bounded {@code double} parameter that can be tuned at runtime or driven by the animation system.
 *
 * <p>{@link #projection(double, double)} creates a view of this parameter with a different
 * min/max range without copying the stored value, which is useful when a parameter needs to
 * be presented under different scales in different contexts.</p>
 */
public class DoubleParameter extends AbstractValue {

  private final double min;
  private final double max;
  private final double defaultValue;

  /** Current value; lies in {@code [min, max]}. */
  public double value;

  /**
   * Creates a parameter with initial value {@code 0.0}.
   *
   * @param description display name
   * @param min         lower bound
   * @param max         upper bound
   */
  public DoubleParameter(String description, double min, double max) {
    this(description, min, max, 0.0);
  }

  /**
   * @param description display name
   * @param min         lower bound
   * @param max         upper bound
   * @param value       initial value (also used as the reset default)
   */
  public DoubleParameter(String description, double min, double max, double value) {
    super(description);
    this.min = min;
    this.max = max;
    this.value = value;
    this.defaultValue = value;
  }

  @Override
  public NodeType getNodeType() {
    return NodeType.DOUBLE;
  }

  public Number getValue() {
    return value;
  }

  public void setValue(Number value) {
    this.value = value.doubleValue();
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
    this.value = min + (max - min) * normalisedValue;
    fireChangeListeners();
  }

  @Override
  public void reset() {
    setValue(defaultValue);
  }

  /**
   * Returns a view of this parameter with a different min/max range.  Reads and writes pass
   * through to the underlying parameter; only the advertised bounds change.  Useful for
   * presenting the same value under a different scale without copying it.
   *
   * @param min new lower bound
   * @param max new upper bound
   * @return a wrapper {@link DoubleParameter} that delegates get/set to this instance
   */
  public DoubleParameter projection(double min, double max) {
    return new DoubleParameter(getName(), min, max) {
      @Override
      public Number getValue() {
        return DoubleParameter.this.getValue();
      }

      @Override
      public void setValue(Number value) {
        DoubleParameter.this.setValue(value);
      }
    };
  }

}
