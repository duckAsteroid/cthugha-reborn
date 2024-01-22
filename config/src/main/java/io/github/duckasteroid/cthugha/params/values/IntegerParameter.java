package io.github.duckasteroid.cthugha.params.values;

import io.github.duckasteroid.cthugha.params.NodeType;
import io.github.duckasteroid.cthugha.params.AbstractValue;

public class IntegerParameter extends AbstractValue {

  private final int min;
  private final int max;

  public int value;

  public IntegerParameter(String description, int min, int max) {
    super(description);
    this.min = min;
    this.max = max;
  }
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
