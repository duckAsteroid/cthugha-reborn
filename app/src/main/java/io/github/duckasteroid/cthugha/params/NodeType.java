package io.github.duckasteroid.cthugha.params;

/**
 * Discriminator for the kind of value held by a {@link Node}.
 *
 * <p>Leaf nodes (those that extend {@link AbstractValue}) carry a concrete value type;
 * interior grouping nodes use {@link #CONTAINER}.</p>
 */
public enum NodeType {
  /** Node holds a boolean (on/off) value – see {@link io.github.duckasteroid.cthugha.params.values.BooleanParameter}. */
  BOOLEAN,
  /** Node holds a bounded {@code double} value – see {@link io.github.duckasteroid.cthugha.params.values.DoubleParameter}. */
  DOUBLE,
  /** Node holds a bounded {@code int} value – see {@link io.github.duckasteroid.cthugha.params.values.IntegerParameter}. */
  INTEGER,
  /** Node holds a bounded {@code long} value – see {@link io.github.duckasteroid.cthugha.params.values.LongParameter}. */
  LONG,
  /** Node holds an enumerated object value – see {@link io.github.duckasteroid.cthugha.params.values.EnumParameter}. */
  ENUM,
  /** Node is an interior grouping node with no value of its own. */
  CONTAINER,
  /** Node is an invocable action — see {@link Action}. */
  ACTION,
  /** Node holds a mutable {@link String} value — see {@link io.github.duckasteroid.cthugha.params.values.StringParameter}. */
  STRING
}
