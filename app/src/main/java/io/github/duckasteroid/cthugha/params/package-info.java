/**
 * Hierarchical, typed parameter system used throughout Cthugha Reborn for runtime tunability.
 *
 * <h2>Overview</h2>
 * <p>Parameters are organised as a composite tree of {@link io.github.duckasteroid.cthugha.params.Node}s.
 * Interior nodes – implemented by {@link io.github.duckasteroid.cthugha.params.ParamNode} –
 * group related parameters.  Leaf nodes are either
 * {@link io.github.duckasteroid.cthugha.params.AbstractValue} subclasses (readable/writable values)
 * or {@link io.github.duckasteroid.cthugha.params.action.Action} instances (invokable operations).</p>
 *
 * <h2>Node type hierarchy</h2>
 * <pre>
 * Node (interface)
 * ├─ ParamNode               – composite grouping node  {@link io.github.duckasteroid.cthugha.params.NodeType#CONTAINER}
 * │  ├─ AbstractValue           – leaf: bounded numeric value
 * │  │  └─ (see params.values)
 * │  ├─ StringValue             – leaf: mutable String value
 * │  ├─ ContainerNode           – anonymous grouping wrapper
 * │  └─ AbstractAction          – leaf: invokable operation  (see params.action)
 * └─ NodeType enum              – discriminator returned by Node.getNodeType()
 * </pre>
 *
 * <h2>Sub-packages</h2>
 * <ul>
 *   <li>{@link io.github.duckasteroid.cthugha.params.values} – concrete typed leaf nodes:
 *       {@code BooleanParameter}, {@code DoubleParameter}, {@code IntegerParameter},
 *       {@code LongParameter}, {@code ObjectParameter}, {@code EnumParameter},
 *       {@code StringParameter}.</li>
 *   <li>{@link io.github.duckasteroid.cthugha.params.action} – the action system:
 *       {@code Action}, {@code AbstractAction}, {@code ActionContext}.</li>
 *   <li>{@link io.github.duckasteroid.cthugha.params.transform} – composite 2-D transform
 *       parameters: {@code XYParam}, {@code PerspectiveParams}, {@code TransformParams}.</li>
 * </ul>
 *
 * <h2>Defining parameters</h2>
 * <p>Declare child parameters as {@code public} fields; the no-arg constructor discovers them
 * via reflection.  Alternatively call {@link io.github.duckasteroid.cthugha.params.ParamNode#initChildren}
 * explicitly from a named constructor:</p>
 * <pre>{@code
 * public class MyRenderer extends ParamNode {
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
 * <h2>Change events</h2>
 * <p>There are two layers of change notification:</p>
 * <ul>
 *   <li><b>Leaf listeners</b> – {@link io.github.duckasteroid.cthugha.params.AbstractValue#addChangeListener}
 *       fires whenever a single parameter's value or controlled state changes.  Used internally
 *       by the remote SSE broadcaster.</li>
 *   <li><b>Subtree listeners</b> – {@link io.github.duckasteroid.cthugha.params.ParamNode#addSubtreeListener}
 *       fires on any ancestor when any descendant value changes, providing the changed node's
 *       full slash-delimited path.  The {@code RemoteEventBroadcaster} attaches subtree listeners
 *       to stream SSE events to connected browser clients.</li>
 * </ul>
 *
 * <h2>Animation integration</h2>
 * <p>{@link io.github.duckasteroid.cthugha.params.AbstractValue#setNormalisedValue(double)} maps a
 * {@code [0, 1]} fraction linearly to the parameter's {@code [min, max]} range.  The animation
 * system ({@code AnimationSystem} / {@code AnimationBinding}) uses this entry point to drive any
 * {@code AbstractValue} from a render-core {@code WaveFunction} once per frame.</p>
 *
 * <h2>Remote visibility</h2>
 * <p>{@link io.github.duckasteroid.cthugha.params.Node#isRemoteAllowed()} controls whether a node
 * appears in the remote API.  Call {@link io.github.duckasteroid.cthugha.params.ParamNode#withNoRemote()}
 * on any node that must not be accessible from a remote browser client (e.g. Quit).
 * The {@code ParamSerializer} omits such nodes from the JSON payload; the {@code RemoteServer}
 * returns 403 for any direct request targeting them.</p>
 *
 * <h2>UI hints</h2>
 * <p>{@link io.github.duckasteroid.cthugha.params.UiHint} defines the hint keys ({@code control-type},
 * {@code icon}, {@code hidden}, …) that guide how the remote React UI renders each node.
 * Apply hints fluently via
 * {@link io.github.duckasteroid.cthugha.params.ParamNode#withUiHint(String, String)}.</p>
 */
package io.github.duckasteroid.cthugha.params;
