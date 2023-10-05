package io.github.duckasteroid.cthugha.params;

/**
 * A value that can be tweaked at runtime
 */
public class DoubleParameter extends RuntimeParameter<Double> {

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

  public Double getValue() {
    return value;
  }

  public void setValue(double value) {
    this.value = value;
  }

  public double getMin() {
    return min;
  }

  public double getMax() {
    return max;
  }


}
