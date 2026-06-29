package io.github.duckasteroid.cthugha.params.values;

import io.github.duckasteroid.cthugha.params.NodeType;
import io.github.duckasteroid.cthugha.params.AbstractValue;
import java.util.function.Function;

/**
 * A boolean (on/off) parameter that is also compatible with the numeric parameter API.
 *
 * <p>Internally the value is stored as a {@code boolean}, but it is exposed via
 * {@link #getValue()} as {@code 1} (true) or {@code 0} (false) so that the animation system
 * can drive it like any other {@link AbstractValue}.  {@link #setValue(Number)} rounds its
 * argument to the nearest integer; any value that rounds to 1 is treated as {@code true}.</p>
 */
public class BooleanParameter extends AbstractValue {

  /** Current on/off state; {@code true} corresponds to the numeric value {@code 1}. */
  public boolean value;

  @SuppressWarnings("unused")
  private static final Function<Integer, Boolean> toBool = (integer -> integer == 1);
  @SuppressWarnings("unused")
  private static final Function<Boolean, Integer> toInt = aBoolean -> aBoolean ? 1 : 0;

  /**
   * Creates a boolean parameter that is initially {@code true}.
   *
   * @param name display name
   */
  public BooleanParameter(String name) {
    this(name, true);
  }

  /**
   * @param name display name
   * @param b    initial value
   */
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
