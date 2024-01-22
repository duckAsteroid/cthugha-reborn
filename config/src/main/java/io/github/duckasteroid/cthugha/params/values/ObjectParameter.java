package io.github.duckasteroid.cthugha.params.values;

import java.util.function.Function;

public class ObjectParameter<T> extends IntegerParameter {

  private final Function<Integer, T> fromInt;
  private final Function<T, Integer> toInt;
  public ObjectParameter(String description, int min, int max, Function<Integer, T> fromInt,
                         Function<T, Integer> toInt) {
    super(description, min, max);
    this.fromInt = fromInt;
    this.toInt = toInt;
  }

  public ObjectParameter(String description, int min, int max, int value,
                         Function<Integer, T> fromInt, Function<T, Integer> toInt) {
    super(description, min, max, value);
    this.fromInt = fromInt;
    this.toInt = toInt;
  }

  public T getObjectValue() {
    return fromInt.apply(getValue().intValue());
  }

  public void setObjectValue(T value) {
    setValue(toInt.apply(value));
  }
}
