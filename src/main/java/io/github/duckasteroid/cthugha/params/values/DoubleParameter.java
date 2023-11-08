package io.github.duckasteroid.cthugha.params.values;

import io.github.duckasteroid.cthugha.params.NodeType;
import io.github.duckasteroid.cthugha.params.AbstractValue;

/**
 * A value that can be tweaked at runtime
 */
public class DoubleParameter extends AbstractValue {

  private final double min;
  private final double max;

  public double value;

  public DoubleParameter(String description, double min, double max) {
    super(description);
    this.min = min;
    this.max = max;
  }
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
  }

  public Number getMin() {
    return min;
  }

  public Number getMax() {
    return max;
  }

  /**
   * Creates a parameter that wraps this parameter but with different min/max values
   * @param min minimum
   * @param max maximum
   * @return a parameter that wraps the underlying parameter and adds new min/max range
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
