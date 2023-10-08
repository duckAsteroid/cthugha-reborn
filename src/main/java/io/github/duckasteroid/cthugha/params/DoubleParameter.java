package io.github.duckasteroid.cthugha.params;

/**
 * A value that can be tweaked at runtime
 */
public class DoubleParameter extends RuntimeParameter {

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
  public Type getType() {
    return Type.DOUBLE;
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

  public DoubleParameter projection(double min, double max) {
    return new DoubleParameter(getDescription(), min, max) {
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
