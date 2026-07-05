package io.github.duckasteroid.cthugha.params;

import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

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
 *
 * <h2>Remote control</h2>
 * <p>{@link #isControlled()} indicates that an internal system (typically the animation system)
 * currently owns this parameter.  Change listeners registered via {@link #addChangeListener}
 * are notified whenever the value or {@code controlled} state changes — used by the remote
 * HTTP server to push SSE events.</p>
 */
public abstract class AbstractValue extends ParamNode {

  private final AtomicBoolean controlled = new AtomicBoolean(false);
  private final CopyOnWriteArrayList<Runnable> changeListeners = new CopyOnWriteArrayList<>();

  /**
   * @param description display name and description of this parameter
   */
  public AbstractValue(String description) {
    super(description);
    withUiHint(UiHint.CONTROL_TYPE, UiHint.SLIDER);
  }

  /**
   * Returns the current value of this parameter.
   *
   * @return current value; never {@code null}
   */
  public abstract Number getValue();

  /** Restores this parameter to its constructor-time default value. */
  public abstract void reset();

  @Override
  public void resetToDefaults() {
    reset();
  }

  /**
   * Sets the current value, clamping or coercing to the concrete type as needed.
   * Concrete subclasses must call {@link #fireChangeListeners()} after updating the stored value.
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

  /** Returns {@code true} when an internal system (e.g. the animation system) currently drives this parameter. */
  public boolean isControlled() {
    return controlled.get();
  }

  /** Called by the animation system when it binds or releases this parameter. Fires change listeners only on transition. */
  public void setControlled(boolean controlled) {
    if (this.controlled.compareAndSet(!controlled, controlled)) {
      fireChangeListeners();
    }
  }

  /** Registers a listener to be called whenever this parameter's value or controlled state changes. */
  public void addChangeListener(Runnable listener) {
    changeListeners.add(listener);
  }

  /** Removes a previously registered change listener. */
  public void removeChangeListener(Runnable listener) {
    changeListeners.remove(listener);
  }

  /** Notifies all registered change listeners and propagates to ancestor subtree listeners. */
  protected void fireChangeListeners() {
    changeListeners.forEach(Runnable::run);
    fireSubtreeListeners(getFullPath());
  }

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
