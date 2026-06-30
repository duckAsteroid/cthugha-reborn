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

  /** Current value; lies in {@code [min, max]}. */
  public double value;

  /**
   * Creates a parameter with initial value equal to {@code min}.
   *
   * @param description display name
   * @param min         lower bound
   * @param max         upper bound
   */
  public DoubleParameter(String description, double min, double max) {
    super(description);
    this.min = min;
    this.max = max;
  }

  /**
   * @param description display name
   * @param min         lower bound
   * @param max         upper bound
   * @param value       initial value
   */
  public DoubleParameter(String description, double min, double max, double value) {
    this(description, min, max);
    this.value = value;
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
