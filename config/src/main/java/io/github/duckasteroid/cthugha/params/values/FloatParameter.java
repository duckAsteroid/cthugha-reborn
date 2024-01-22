package io.github.duckasteroid.cthugha.params.values;

import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.NodeType;

/**
 * A float value that can be tweaked at runtime
 */
public class FloatParameter extends AbstractValue {

  private final float min;
  private final float max;

  public float value;

  public FloatParameter(String description, float min, float max) {
    super(description);
    this.min = min;
    this.max = max;
  }
  public FloatParameter(String description, float min, float max, float value) {
    this(description, min, max);
    this.value = value;
  }

  @Override
  public NodeType getNodeType() {
    return NodeType.FLOAT;
  }

  public Number getValue() {
    return value;
  }

  public void setValue(Number value) {
    this.value = value.floatValue();
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
  public FloatParameter projection(float min, float max) {
    return new FloatParameter(getName(), min, max) {
      @Override
      public Number getValue() {
        return FloatParameter.this.getValue();
      }

      @Override
      public void setValue(Number value) {
        FloatParameter.this.setValue(value);
      }
    };
  }

}
