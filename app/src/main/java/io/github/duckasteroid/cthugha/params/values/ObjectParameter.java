package io.github.duckasteroid.cthugha.params.values;

import java.util.function.Function;

/**
 * An {@link IntegerParameter} that maps its integer index to/from an arbitrary object type
 * using caller-supplied conversion functions.
 *
 * <p>The underlying stored value is always an integer index.  {@link #getObjectValue()} maps
 * the index to an object via {@code fromInt}, and {@link #setObjectValue(Object)} maps back
 * via {@code toInt}.  The animation system operates on the integer index directly, so animated
 * transitions step discretely through the object sequence.</p>
 *
 * @param <T> the object type this parameter represents
 * @see EnumParameter
 */
public class ObjectParameter<T> extends IntegerParameter {

  /** Converts a stored integer index to the corresponding object. */
  private final Function<Integer, T> fromInt;

  /** Converts an object back to its integer index for storage. */
  private final Function<T, Integer> toInt;

  /**
   * @param description display name
   * @param min         minimum index value
   * @param max         maximum index value (exclusive upper bound for the object sequence)
   * @param fromInt     maps index → object
   * @param toInt       maps object → index
   */
  public ObjectParameter(String description, int min, int max, Function<Integer, T> fromInt,
                         Function<T, Integer> toInt) {
    super(description, min, max);
    this.fromInt = fromInt;
    this.toInt = toInt;
  }

  /**
   * @param description display name
   * @param min         minimum index value
   * @param max         maximum index value
   * @param value       initial index value
   * @param fromInt     maps index → object
   * @param toInt       maps object → index
   */
  public ObjectParameter(String description, int min, int max, int value,
                         Function<Integer, T> fromInt, Function<T, Integer> toInt) {
    super(description, min, max, value);
    this.fromInt = fromInt;
    this.toInt = toInt;
  }

  /**
   * Returns the object corresponding to the current integer index.
   *
   * @return the current object value
   */
  public T getObjectValue() {
    return fromInt.apply(getValue().intValue());
  }

  /**
   * Sets the current value by looking up {@code value}'s index via the {@code toInt} function.
   *
   * @param value the object to select
   */
  public void setObjectValue(T value) {
    setValue(toInt.apply(value));
  }
}
