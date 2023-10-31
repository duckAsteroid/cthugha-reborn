package io.github.duckasteroid.cthugha.params;

import java.awt.BasicStroke;
import java.util.Random;
import java.util.function.Predicate;

public abstract class RuntimeParameter {
  protected final static Random random = new Random();
  public enum Type {
    BOOLEAN, DOUBLE, INTEGER, LONG, ENUM
  }
  private final String description;

  public RuntimeParameter(String description) {
    this.description = description;
  }

  public abstract Type getType();

  public abstract Number getValue();
  public abstract void setValue(Number d);

  public abstract Number getMin();
  public abstract Number getMax();

  public void randomise() {
    setValue(random.nextDouble(getMin().doubleValue(), getScale()));
  }

  public Double getScale() {
    return getMax().doubleValue() - getMin().doubleValue();
  }

  public void setValue(Fraction f) {
    final double value = getMin().doubleValue() + (getScale() * f.fraction);
    setValue(value);
  }
  public String getDescription() {
    return description;
  }

  @Override
  public String toString() {
    return description + " [" + getType().name() + "]: " +
      getValue() + " (" + getMin() + " - " + getMax() + ")";
  }
}
