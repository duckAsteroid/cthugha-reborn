package io.github.duckasteroid.cthugha.params;

import java.util.Random;

/**
 * The base interface for all {@link Node}s that are actually a modifiable value. We use Number
 * as the foundation for all values.
 */
public abstract class AbstractValue extends AbstractNode {
  /**
   * random number generator for {@link #randomise()}
   */
  protected final static Random random = new Random();

  public AbstractValue(String description) {
    super(description);
  }

  /**
   * The current value of this parameter
   */
  public abstract Number getValue();

  /**
   * Set the current value of this
   */
  public abstract void setValue(Number d);

  /**
   * The minimum of this value
   */
  public abstract Number getMin();

  /**
   * The maximum of this value
   */
  public abstract Number getMax();

  /**
   * Select a new random value somewhere between {@link #getMin()} and {@link #getMax()}
   */
  public void randomise() {
    setValue(random.nextDouble(getMin().doubleValue(), getScale()));
  }

  /**
   * The difference between min and max
   */
  public Double getScale() {
    return getMax().doubleValue() - getMin().doubleValue();
  }

  /**
   * Set the value as a fraction (0-1) of the {@link #getScale()}
   */
  public void setValue(Fraction f) {
    final double value = getMin().doubleValue() + (getScale() * f.fraction);
    setValue(value);
  }

  @Override
  public String toString() {
    return getName() + " [" + getNodeType().name() + "]: " +
      getValue() + " (" + getMin() + " - " + getMax() + ")";
  }
}
