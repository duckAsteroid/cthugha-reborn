package io.github.duckasteroid.cthugha.params.values;

import io.github.duckasteroid.cthugha.params.NodeType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An object parameter that uses the integer index of objects in an
 * enumerated list to map to an integer
 * @param <T> the type of object enumerated
 */
public class EnumParameter<T> extends ObjectParameter<T> {

  private final List<T> values;

  public EnumParameter(String description, final List<T> values) {
    super(description, 0, values.size(), values::get, values::indexOf);
    this.values = values;
  }

  /**
   * Create from a Java {@link Enum} type
   * @param enumType the enum class
   * @return an {@link EnumParameter} instance for the enum type
   * @param <T> the type of enum wrapped
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

  public T getEnumeration() {
    return getObjectValue();
  }

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
