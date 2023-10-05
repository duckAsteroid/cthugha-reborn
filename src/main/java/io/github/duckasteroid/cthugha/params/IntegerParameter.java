package io.github.duckasteroid.cthugha.params;

public class IntegerParameter extends RuntimeParameter<Integer> {

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
  public Type getType() {
    return Type.INTEGER;
  }

  public Integer getValue() {
    return value;
  }

  public void setValue(int value) {
    this.value = value;
  }

  public int getMin() {
    return min;
  }

  public int getMax() {
    return max;
  }

}
