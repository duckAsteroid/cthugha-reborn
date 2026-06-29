/**
 * Hierarchical, typed parameter system used throughout Cthugha Reborn for runtime tunability.
 *
 * <h2>Overview</h2>
 * <p>Parameters are organised as a composite (tree) of {@link io.github.duckasteroid.cthugha.params.Node}s.
 * Interior nodes – implemented by {@link io.github.duckasteroid.cthugha.params.AbstractNode} –
 * group related parameters.  Leaf nodes are concrete
 * {@link io.github.duckasteroid.cthugha.params.AbstractValue} subclasses that each hold a single
 * typed, bounded value.</p>
 *
 * <h2>Node hierarchy</h2>
 * <pre>
 * Node (interface)
 * └─ AbstractNode          – composite grouping node
 *    └─ AbstractValue      – leaf value node (bounded numeric value)
 *       ├─ BooleanParameter
 *       ├─ DoubleParameter
 *       ├─ IntegerParameter
 *       ├─ LongParameter
 *       └─ ObjectParameter
 *          └─ EnumParameter
 * </pre>
 *
 * <h2>Composite parameter nodes</h2>
 * <p>Higher-level parameters composed of simpler ones live here too:</p>
 * <ul>
 *   <li>{@link io.github.duckasteroid.cthugha.params.XYParam} – a pair of {@code DoubleParameter}s
 *       representing a 2-D coordinate or scale factor.</li>
 *   <li>{@link io.github.duckasteroid.cthugha.params.AffineTransformParams} – a full affine
 *       transform (scale, shear, translate, rotate) expressed as named child parameters and
 *       convertible to a {@link java.awt.geom.AffineTransform}.</li>
 * </ul>
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * // Define parameters as public fields so AbstractNode can discover them via reflection.
 * public class MyRenderer extends AbstractNode {
 *     public final DoubleParameter speed = new DoubleParameter("Speed", 0.0, 10.0, 1.0);
 *     public final BooleanParameter enabled = new BooleanParameter("Enabled", true);
 *
 *     public MyRenderer() {
 *         super("MyRenderer");
 *         initChildren(speed, enabled);
 *     }
 * }
 * }</pre>
 *
 * <h2>Integration with render-core timer functions</h2>
 * <p>{@link io.github.duckasteroid.cthugha.params.AbstractValue#setNormalisedValue(double)}
 * accepts a normalised {@code [0, 1]} value and maps it linearly to the parameter's
 * {@code [min, max]} range.  Use this to wire render-core timer functions such as
 * {@code WaveFunction} and {@code LinearFunction} to any parameter, e.g.:<br>
 * {@code myParam.setNormalisedValue((waveFunction.value() + 1) / 2)}</p>
 *
 * <h2>Leaf value types</h2>
 * <p>Leaf types are in the {@code values} sub-package
 * ({@link io.github.duckasteroid.cthugha.params.values}).</p>
 */
package io.github.duckasteroid.cthugha.params;
