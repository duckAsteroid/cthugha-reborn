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

  /** Current value; lies in {@code [min, max]}. */
  public long value;

  /**
   * Creates a parameter with initial value equal to {@code min}.
   *
   * @param description display name
   * @param min         lower bound (inclusive)
   * @param max         upper bound (inclusive)
   */
  public LongParameter(String description, long min, long max) {
    super(description);
    this.min = min;
    this.max = max;
  }

  /**
   * @param description display name
   * @param min         lower bound (inclusive)
   * @param max         upper bound (inclusive)
   * @param value       initial value
   */
  public LongParameter(String description, long min, long max, long value) {
    this(description, min, max);
    this.value = value;
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

}
