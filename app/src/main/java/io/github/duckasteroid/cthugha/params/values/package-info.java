/**
 * Concrete leaf-node implementations of
 * {@link io.github.duckasteroid.cthugha.params.AbstractValue}.
 *
 * <p>Each class wraps a single bounded value of a specific Java primitive type.  All types
 * share the common {@link io.github.duckasteroid.cthugha.params.AbstractValue} contract
 * (min, max, get/set as {@link java.lang.Number}, fraction-based set for animation).</p>
 *
 * <ul>
 *   <li>{@link io.github.duckasteroid.cthugha.params.values.BooleanParameter} – on/off flag;
 *       exposed numerically as 0 or 1 for animation compatibility.</li>
 *   <li>{@link io.github.duckasteroid.cthugha.params.values.DoubleParameter} – bounded
 *       {@code double} with optional {@code projection()} for range remapping.</li>
 *   <li>{@link io.github.duckasteroid.cthugha.params.values.IntegerParameter} – bounded
 *       {@code int}; {@code setValue} rounds via {@link java.lang.Math#round}.</li>
 *   <li>{@link io.github.duckasteroid.cthugha.params.values.LongParameter} – bounded
 *       {@code long} for high-precision counters or timestamps.</li>
 *   <li>{@link io.github.duckasteroid.cthugha.params.values.ObjectParameter} – wraps an
 *       arbitrary object type by storing its integer index, using caller-supplied
 *       {@link java.util.function.Function}s to convert between index and object.</li>
 *   <li>{@link io.github.duckasteroid.cthugha.params.values.EnumParameter} – specialisation
 *       of {@code ObjectParameter} for {@link java.lang.Enum} types and ordered lists.</li>
 * </ul>
 */
package io.github.duckasteroid.cthugha.params.values;
