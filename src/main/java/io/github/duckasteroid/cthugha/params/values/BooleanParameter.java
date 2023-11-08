package io.github.duckasteroid.cthugha.params.values;

import io.github.duckasteroid.cthugha.params.NodeType;
import io.github.duckasteroid.cthugha.params.AbstractValue;
import java.util.function.Function;

public class BooleanParameter extends AbstractValue {
  public boolean value;
  private static final Function<Integer, Boolean> toBool = (integer -> integer == 1);
  private static final Function<Boolean, Integer> toInt = aBoolean -> aBoolean ? 1 : 0;

  public BooleanParameter(String name) {
    this(name, true);
  }

  public BooleanParameter(String name, boolean b) {
    super(name);
    this.value = b;
  }

  @Override
  public NodeType getNodeType() {
    return NodeType.BOOLEAN;
  }

  @Override
  public Number getMin() {
    return 0;
  }

  @Override
  public Number getMax() {
    return 1;
  }

  @Override
  public Number getValue() {
    return value ? 1 : 0;
  }

  @Override
  public void setValue(Number d) {
    int value = (int)Math.round(d.doubleValue());
    this.value = value == 1;
  }

  @Override
  public String toString() {
    return getName() + " [" + getNodeType().name() + "]: " + value;
  }
}
