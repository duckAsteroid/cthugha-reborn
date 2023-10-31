package io.github.duckasteroid.cthugha.params;

public class LongParameter extends RuntimeParameter {

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
  public Type getType() {
    return Type.LONG;
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
