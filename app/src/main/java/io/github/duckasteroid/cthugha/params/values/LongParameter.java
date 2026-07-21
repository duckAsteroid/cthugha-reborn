package io.github.duckasteroid.cthugha.params.values;

import io.github.duckasteroid.cthugha.params.NodeType;
import io.github.duckasteroid.cthugha.params.AbstractValue;

/**
 * A bounded {@code long} parameter, suited for high-precision counters or nanosecond timestamps.
 *
 * <p>{@link #setValue(Number)} calls {@link Number#longValue()}, which truncates any fractional
 * part, so animations targeting this parameter will snap to integer {@code long} values.</p>
 */
public class LongParameter extends AbstractValue {

  private final long min;
  private final long max;
  private final long defaultValue;

  /** Current value; lies in {@code [min, max]}. */
  public long value;

  /**
   * Creates a parameter with initial value {@code 0}.
   *
   * @param description display name
   * @param min         lower bound (inclusive)
   * @param max         upper bound (inclusive)
   */
  public LongParameter(String description, long min, long max) {
    this(description, min, max, 0L);
  }

  /**
   * @param description display name
   * @param min         lower bound (inclusive)
   * @param max         upper bound (inclusive)
   * @param value       initial value (also used as the reset default)
   */
  public LongParameter(String description, long min, long max, long value) {
    super(description);
    this.min = min;
    this.max = max;
    this.value = value;
    this.defaultValue = value;
  }

  @Override
  public NodeType getNodeType() {
    return NodeType.LONG;
  }

  public Number getValue() {
    return value;
  }

  public void setValue(Number value) {
    this.value = value.longValue();
    fireChangeListeners();
  }

  public Number getMin() {
    return min;
  }

  public Number getMax() {
    return max;
  }

  @Override
  public void setNormalisedValue(double normalisedValue) {
    this.value = Math.round(min + (max - min) * normalisedValue);
    fireChangeListeners();
  }

  @Override
  public void reset() {
    setValue(defaultValue);
  }

}
