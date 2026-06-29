package io.github.duckasteroid.cthugha.params;

import java.util.Random;

/**
 * Base class for all leaf parameter nodes that hold a bounded, mutable numeric value.
 *
 * <p>Every value has a {@link #getMin() minimum} and {@link #getMax() maximum}.  The current
 * value must always lie within this range.  Concrete subclasses store the value in a
 * type-appropriate primitive field and implement {@link #getValue()}/{@link #setValue(Number)}
 * accordingly.</p>
 *
 * <h2>Driving from normalised values</h2>
 * <p>{@link #setNormalisedValue(double)} accepts a normalised fraction in {@code [0, 1]}, maps
 * it linearly to {@code [min, max]}, and delegates to {@link #setValue(Number)}.  This is the
 * natural entry point for render-core timer functions ({@code WaveFunction}, {@code LinearFunction},
 * etc.) which produce normalised {@code double} outputs.</p>
 *
 * <h2>Randomisation</h2>
 * <p>{@link #randomise()} picks a uniform random value within {@code [min, max]}, which is
 * useful for generative effect variation.</p>
 */
public abstract class AbstractValue extends AbstractNode {


  /**
   * @param description display name and description of this parameter
   */
  public AbstractValue(String description) {
    super(description);
  }

  /**
   * Returns the current value of this parameter.
   *
   * @return current value; never {@code null}
   */
  public abstract Number getValue();

  /**
   * Sets the current value, clamping or coercing to the concrete type as needed.
   *
   * @param d new value; the concrete type determines how it is stored
   */
  public abstract void setValue(Number d);

  /**
   * Returns the minimum allowed value for this parameter.
   *
   * @return lower bound; never {@code null}
   */
  public abstract Number getMin();

  /**
   * Returns the maximum allowed value for this parameter.
   *
   * @return upper bound; never {@code null}
   */
  public abstract Number getMax();

  /**
   * Sets this parameter to a uniformly random value in {@code [min, max]}.
   *
   * @param rng the random source to use (typically {@code ctx.getRandom()})
   */
  @Override
  public void randomise(Random rng) {
    setValue(getMin().doubleValue() + rng.nextDouble() * getScale());
  }

  /**
   * Returns the range of this parameter: {@code max - min}.
   *
   * @return the distance between min and max
   */
  public Double getScale() {
    return getMax().doubleValue() - getMin().doubleValue();
  }

  /**
   * Sets the value from a normalised fraction in {@code [0, 1]}, mapping it linearly to
   * {@code [min, max]}.  Suitable for use with render-core timer functions such as
   * {@code WaveFunction} and {@code LinearFunction} which produce normalised {@code double}
   * outputs.
   *
   * @param normalisedValue normalised fraction in {@code [0, 1]}; 0 maps to {@link #getMin()},
   *                        1 maps to {@link #getMax()}
   */
  public void setNormalisedValue(double normalisedValue) {
    setValue(getMin().doubleValue() + (getScale() * normalisedValue));
  }

  @Override
  public String toString() {
    return getName() + " [" + getNodeType().name() + "]: " +
      getValue() + " (" + getMin() + " - " + getMax() + ")";
  }
}
