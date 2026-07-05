/**
 * Composite parameter nodes for 2-D spatial transforms.
 *
 * <h2>Overview</h2>
 * <p>These classes are building blocks for effects that need tunable geometric parameters.
 * They are built on top of the core param framework
 * ({@link io.github.duckasteroid.cthugha.params.AbstractNode},
 * {@link io.github.duckasteroid.cthugha.params.values.DoubleParameter}) and are used by both
 * wave models ({@code display.wave}) and tab generators ({@code tab.generators}).</p>
 *
 * <h2>Types</h2>
 * <ul>
 *   <li>{@link io.github.duckasteroid.cthugha.params.transform.XYParam} – a pair of bounded
 *       {@code double} parameters sharing the same min/max range, representing a 2-D point or
 *       vector (e.g. scale factor, centre point, translation offset).  Provides
 *       {@link io.github.duckasteroid.cthugha.params.transform.XYParam#is(java.util.function.DoublePredicate)}
 *       for efficient identity checks and
 *       {@link io.github.duckasteroid.cthugha.params.transform.XYParam#toVector2f()} for JOML
 *       interop.</li>
 *   <li>{@link io.github.duckasteroid.cthugha.params.transform.PerspectiveParams} – optional
 *       perspective projection: an {@code enabled} flag, vertical field-of-view in degrees, and
 *       z-distance.  Used as a child of {@code TransformParams}.</li>
 *   <li>{@link io.github.duckasteroid.cthugha.params.transform.TransformParams} – a full
 *       affine/perspective transform expressed as tunable parameter groups (perspective, scale,
 *       shear, translate, rotate around a configurable centre).  Call
 *       {@link io.github.duckasteroid.cthugha.params.transform.TransformParams#applyTo(org.joml.Matrix4f)}
 *       each frame to accumulate only the active (non-identity) components into a JOML
 *       {@link org.joml.Matrix4f}.</li>
 * </ul>
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * public class MyWaveModel extends AbstractNode {
 *     public final TransformParams transform = new TransformParams("transform");
 *
 *     public MyWaveModel() { super("MyWaveModel"); initFields(getClass()); }
 *
 *     public void render(Matrix4f mvp) {
 *         transform.applyTo(mvp);
 *         // ... draw with mvp
 *     }
 * }
 * }</pre>
 */
package io.github.duckasteroid.cthugha.params.transform;
