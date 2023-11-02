package io.github.duckasteroid.cthugha.params;

public class BooleanParameter extends RuntimeParameter {
  public boolean value;

  public BooleanParameter(String name) {
    this(name, true);
  }

  public BooleanParameter(String name, boolean b) {
    super(name);
    this.value = b;
  }

  @Override
  public Type getType() {
    return Type.BOOLEAN;
  }

  @Override
  public Number getValue() {
    return value ? 1 : 0;
  }

  @Override
  public void setValue(Number d) {
    this.value = d.intValue() == 1;
  }

  @Override
  public Number getMax() {
    return 1;
  }

  public Number getMin() {
    return 0;
  }
}
