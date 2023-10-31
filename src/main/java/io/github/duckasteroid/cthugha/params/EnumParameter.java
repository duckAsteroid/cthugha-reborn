package io.github.duckasteroid.cthugha.params;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class EnumParameter<T extends Enum> extends IntegerParameter {

  private final List<T> values;

  public EnumParameter(String description, List<T> values) {
    super(description, 0, values.size());
    this.values = values;
  }

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
  public Type getType() {
    return Type.ENUM;
  }

  public T getEnumeration() {
    return values.get(value);
  }

  public void setEnumeration(T value) {
    setValue(values.indexOf(value));
  }

  @Override
  public String toString() {
    return super.getDescription()+ " [" + getType().name() + "]: " + getEnumeration().toString()
      + " " + values.stream().map(Objects::toString)
        .collect(Collectors.joining(",", "[", "]"));
  }
}
