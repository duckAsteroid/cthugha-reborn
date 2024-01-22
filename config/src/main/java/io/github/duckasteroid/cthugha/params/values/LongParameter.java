package io.github.duckasteroid.cthugha.params.values;

import io.github.duckasteroid.cthugha.params.NodeType;
import io.github.duckasteroid.cthugha.params.AbstractValue;

public class LongParameter extends AbstractValue {

  private final long min;
  private final long max;

  public long value;

  public LongParameter(String description, long min, long max) {
    super(description);
    this.min = min;
    this.max = max;
  }
  public LongParameter(String description, long min, long max, long value) {
    this(description, min, max);
    this.value = value;
  }

  @Override
  public NodeType getNodeType() {
    return NodeType.LONG;
  }

  public Number getValue() {
    return value;
  }

  public void setValue(Number value) {
    this.value = value.longValue();
  }

  public Number getMin() {
    return min;
  }

  public Number getMax() {
    return max;
  }

}
