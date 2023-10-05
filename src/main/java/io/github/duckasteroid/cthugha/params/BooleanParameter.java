package io.github.duckasteroid.cthugha.params;

public class BooleanParameter extends RuntimeParameter<Boolean> {
  public boolean value;

  public BooleanParameter(String name, boolean b) {
    super(name);
    this.value = b;
  }

  @Override
  public Type getType() {
    return Type.BOOLEAN;
  }

  @Override
  public Boolean getValue() {
    return value;
  }
}
