package io.github.duckasteroid.cthugha.params.values;

import io.github.duckasteroid.cthugha.params.NodeType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An {@link ObjectParameter} whose valid values are the elements of an ordered list (or a
 * Java {@link Enum}).
 *
 * <p>The list index is the stored integer.  {@link #getEnumeration()} and
 * {@link #setEnumeration(Object)} provide type-safe access to the current element.
 * {@link #forType(Class)} is a convenience factory that builds an instance from any Java
 * enum type via reflection.</p>
 *
 * @param <T> the enumerated element type
 */
public class EnumParameter<T> extends ObjectParameter<T> {

  private final List<T> values;

  /**
   * @param description display name
   * @param values      ordered list of allowed values; the list index is the stored integer
   */
  public EnumParameter(String description, final List<T> values) {
    super(description, 0, values.size(), values::get, values::indexOf);
    this.values = values;
  }

  /**
   * Creates an {@link EnumParameter} for all constants of the given Java enum type, in their
   * declaration order.
   *
   * @param enumType the enum class
   * @param <T>      the enum type
   * @return an {@link EnumParameter} whose values are the enum's constants
   */
  public static <T extends Enum> EnumParameter<T> forType(Class<T> enumType) {
    try {
      Method m = enumType.getMethod("values");
      T[] values = (T[]) m.invoke(null);
      return new EnumParameter<>(enumType.getName(), Arrays.asList(values));
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }
  @Override
  public NodeType getNodeType() {
    return NodeType.ENUM;
  }

  /** Returns the currently selected element. */
  public T getEnumeration() {
    return getObjectValue();
  }

  /**
   * Selects the given element; its index in the list becomes the stored integer value.
   *
   * @param value the element to select
   */
  public void setEnumeration(T value) {
    setObjectValue(value);
  }

  @Override
  public String toString() {
    return super.getName()+ " [" + getNodeType().name() + "]: " + getEnumeration().toString()
      + " " + values.stream().map(Objects::toString)
        .collect(Collectors.joining(",", "[", "]"));
  }
}
