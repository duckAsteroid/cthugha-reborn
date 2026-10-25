package io.github.duckasteroid.cthugha.params.transform;

import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.UiHint;

import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.GeometricDoubleParameter;
import io.github.duckasteroid.cthugha.params.transform.animation.AnimationList;
import io.github.duckasteroid.cthugha.params.transform.animation.TransformOffsets;
import org.joml.Matrix4f;

import java.util.function.DoublePredicate;

/**
 * A composite parameter node that exposes the components of a 2-D transform as individually
 * tunable, animatable parameters, led by the ones most often animated:
 *
 * <ul>
 *   <li>{@link #zoom} – uniform scale, 0.25×–4× on a geometric scale, so 1× sits at the middle
 *       of a binding's [0,1] and {@code sine()} pulses evenly around normal size.</li>
 *   <li>{@link #rotation} – fixed angle in degrees, 0–360, about {@link #pivot}.</li>
 *   <li>{@link #spin} – continuous rotation in revolutions per second (negative = anticlockwise),
 *       accumulated from elapsed time and added to {@link #rotation}.</li>
 *   <li>{@link #stretch} – X/Y scale factors multiplied with {@link #zoom}, for unequal
 *       stretching (a collapsed group in the remote UI).</li>
 *   <li>{@link #pivot} – the rotation centre in NDC space (origin = screen centre).</li>
 *   <li>{@link #translate} – X/Y offset in NDC units.</li>
 *   <li>{@link #shear} – X/Y shear factors.</li>
 *   <li>{@link #perspective} – optional perspective projection, with Turn and Tip (and their
 *       spins) rotating the element in depth; used automatically when one of those, or a 3-D
 *       animation package, is active.</li>
 *   <li>{@link #animations} – a stack of animation packages (Breathe, Coin Spin, …) whose
 *       per-frame effect {@code applyTo()} combines with the values above: zoom-like factors
 *       multiply, angles and positions add, so the values above are never overwritten.</li>
 * </ul>
 *
 * <p>{@link #applyTo(Matrix4f, float)} applies them to the geometry in a fixed order: rotate
 * (about the pivot, aspect-corrected) → shear → scale → translate, inside the perspective
 * projection when it is enabled. Rotation and scale therefore act in the element's own space,
 * so a translated element spins and zooms in place, and scaling or rotating never amplifies a
 * translation. Each component is skipped when it is at its identity value (to 10 decimal
 * places).</p>
 */
public class TransformParams extends ParamNode {

  /** Longest gap between {@link #applyTo} calls that {@link #spin} advances over, in seconds. */
  static final double MAX_SPIN_STEP_SECONDS = 0.25;

  /** Uniform scale multiplier; identity 1.0, geometric so 1.0 is mid-range. */
  public final GeometricDoubleParameter zoom = new GeometricDoubleParameter("Zoom", 0.25, 4.0, 1.0);

  /** Fixed rotation angle in degrees, in {@code [0, 360]}. */
  public final DoubleParameter rotation = new DoubleParameter("Rotation", 0, 360, 0);

  /** Continuous rotation speed in revolutions per second; negative is anticlockwise. */
  public final DoubleParameter spin = new DoubleParameter("Spin", -5, 5, 0);

  /** X and Y scale factors, multiplied with {@link #zoom}; identity value is 1.0. */
  public final XYParam stretch = new XYParam("Stretch", 0.05, 10.0, 1.0);

  /** Centre point for rotation in NDC space (origin = screen centre). */
  public final XYParam pivot = new XYParam("Pivot", -1, 1, 0).withPadControl();

  /** X and Y translation offsets in NDC units; identity value is 0.0. */
  public final XYParam translate = new XYParam("Translation", -2.0, 2.0, 0.0);

  /** X and Y shear factors; identity value is 0.0 for both components. */
  public final XYParam shear = new XYParam("Shear", -3.0, 3.0, 0.0);

  /** Optional perspective projection applied around the affine components. */
  public final PerspectiveParams perspective = new PerspectiveParams();

  /** Animation packages stacked on top of the values above. */
  public final AnimationList animations = new AnimationList();

  /** Angles {@link #spin} and the perspective spins have turned through, in degrees within {@code [0, 360)}. GL thread only. */
  private double spinDegrees;
  private double turnSpinDegrees;
  private double tipSpinDegrees;
  /** {@link System#nanoTime()} of the last {@link #applyTo}; {@code -1} before the first. */
  private long lastApplyNanos = -1;
  /** This frame's combined animation effect; reused every frame. GL thread only. */
  private final TransformOffsets offsets = new TransformOffsets();

  /**
   * @param name display name for this transform parameter group
   */
  public TransformParams(String name) {
    super(name);
    rotation.withUiHint(UiHint.ICON, "rotate-cw");
    initChildren(zoom, rotation, spin, stretch, pivot, translate, shear, perspective, animations);
    animations.withUiHint(UiHint.DEFAULT_OPEN, "true");

    zoom.withDescription("Uniform size: 1 is normal, 0.25 a quarter, 4 four times. Multiplies "
        + "Stretch X/Y. Animating it with sine() pulses evenly around normal size.");
    rotation.withDescription("Fixed rotation angle in degrees, around Pivot. Spin turns on top of it.");
    spin.withDescription("Continuous rotation in revolutions per second around Pivot; negative "
        + "turns anticlockwise, 0 stops.");
    stretch.withDescription("X/Y scale factors, multiplied by Zoom, for stretching one axis more "
        + "than the other. 1.0 on both axes is no stretch.");
    pivot.withDescription("Centre of Rotation and Spin, in NDC space where (0,0) is the screen "
        + "centre. Away from the element, Spin makes it orbit this point.");
    translate.withDescription("X/Y translation offset in NDC units. 0.0 on both axes is "
        + "identity (no offset).");
    shear.withDescription("X/Y shear factors applied to the geometry. 0.0 on both axes is "
        + "identity (no shear).");
    perspective.withDescription("Optional 3-D perspective projection around the affine "
        + "components above.");
  }

  /** Returns a predicate that is true when a double equals {@code expected} to {@code dp} decimal places. */
  private static DoublePredicate equals(final double expected, final int dp) {
    final double factor = Math.pow(10, dp);
    return (actual) -> Math.round(actual * factor) == Math.round(expected * factor);
  }

  /** Predicate that matches 1.0 to 10 decimal places; used to detect identity scale. */
  public final DoublePredicate UNITY = equals(1.0, 10);

  /** Predicate that matches 0.0 to 10 decimal places; used to detect identity translate/shear. */
  public final DoublePredicate ZERO = equals(0.0, 10);

  /**
   * Applies the active components of this parameter group to {@code matrix} and returns it,
   * first advancing {@link #spin} by the time since the last call (at most
   * {@value #MAX_SPIN_STEP_SECONDS} s, so a stall doesn't jump the angle). The supplied matrix
   * is mutated in place. All coordinates are in NDC space. GL thread only.
   *
   * <p>If {@link PerspectiveParams#enabled} is true, a perspective projection followed by the
   * configured z-distance translation is applied first, making the subsequent affine components
   * act as model transforms within the 3-D view.</p>
   *
   * @param matrix the transform to modify
   * @param aspect viewport width / height; pass 1.0 if unknown or the viewport is square
   * @return the same {@code matrix} instance, after applying the active components
   */
  public Matrix4f applyTo(Matrix4f matrix, float aspect) {
    return applyTo(matrix, aspect, System.nanoTime());
  }

  /** As {@link #applyTo(Matrix4f, float)}, at an explicit time ({@link System#nanoTime()} scale). */
  Matrix4f applyTo(Matrix4f matrix, float aspect, long nowNanos) {
    double dt = advance(nowNanos);
    offsets.reset();
    animations.apply(offsets, dt, aspect);
    double turn = perspective.turn.value + turnSpinDegrees + offsets.turn;
    double tip = perspective.tip.value + tipSpinDegrees + offsets.tip;
    boolean threeD = offsets.threeD || !ZERO.test(turn % 360.0) || !ZERO.test(tip % 360.0)
        || perspective.turnSpin.value != 0 || perspective.tipSpin.value != 0;
    if (perspective.enabled.value || threeD) {
      double fovY = Math.toRadians(perspective.fovY.value);
      // While enabled, the configured distance; otherwise the one at which the z = 0 plane
      // projects at its normal size, so switching depth on doesn't resize the element.
      double distance = (perspective.enabled.value ? perspective.zDistance.value
          : 1.0 / Math.tan(fovY / 2)) * offsets.distance;
      matrix.perspective((float) fovY, 1.0f, 0.1f, (float) Math.max(10.0, distance * 4))
          .translate(0f, 0f, (float) -distance)
          .rotateY((float) Math.toRadians(turn))
          .rotateX((float) Math.toRadians(tip));
    }
    double translateX = translate.x.value + offsets.translateX;
    double translateY = translate.y.value + offsets.translateY;
    if (!ZERO.test(translateX) || !ZERO.test(translateY)) {
      matrix.translate((float) translateX, (float) translateY, 0f);
    }
    double scaleX = zoom.value * offsets.zoom * stretch.x.value * offsets.stretchX;
    double scaleY = zoom.value * offsets.zoom * stretch.y.value * offsets.stretchY;
    if (!UNITY.test(scaleX) || !UNITY.test(scaleY)) {
      matrix.scale((float) scaleX, (float) scaleY, 1f);
    }
    if (!shear.is(ZERO)) {
      float shx = (float) shear.x.value;
      float shy = (float) shear.y.value;
      // Shear: x' = x + shx*y,  y' = shy*x + y
      // Column-major Matrix4f: col0=(1,shy,0,0), col1=(shx,1,0,0), col2=(0,0,1,0), col3=(0,0,0,1)
      matrix.mul(new Matrix4f(
          1f, shy, 0f, 0f,
          shx, 1f, 0f, 0f,
          0f, 0f, 1f, 0f,
          0f, 0f, 0f, 1f
      ));
    }
    double degrees = (rotation.value + spinDegrees + offsets.rotation) % 360.0;
    if (!ZERO.test(degrees)) {
      float cx = (float) pivot.x.value;
      float cy = (float) pivot.y.value;
      float angle = (float) Math.toRadians(degrees);
      float invAspect = aspect == 0f ? 1f : 1f / aspect;
      // Rotating raw NDC coordinates squishes on a non-square viewport (x and y NDC units cover
      // different physical extents); scale into aspect-corrected units, rotate, then scale back.
      matrix.translate(cx, cy, 0f)
          .scale(invAspect, 1f, 1f)
          .rotateZ(angle)
          .scale(aspect, 1f, 1f)
          .translate(-cx, -cy, 0f);
    }
    return matrix;
  }

  /**
   * Seconds since the previous call (0 on the first, at most {@value #MAX_SPIN_STEP_SECONDS}),
   * after turning the spin angles by their speeds over that time.
   */
  private double advance(long nowNanos) {
    double seconds = 0;
    if (lastApplyNanos >= 0) {
      seconds = Math.min(MAX_SPIN_STEP_SECONDS, Math.max(0, (nowNanos - lastApplyNanos) / 1e9));
      spinDegrees = wrapDegrees(spinDegrees + spin.value * 360.0 * seconds);
      turnSpinDegrees = wrapDegrees(turnSpinDegrees + perspective.turnSpin.value * 360.0 * seconds);
      tipSpinDegrees = wrapDegrees(tipSpinDegrees + perspective.tipSpin.value * 360.0 * seconds);
    }
    lastApplyNanos = nowNanos;
    return seconds;
  }

  private static double wrapDegrees(double degrees) {
    double d = degrees % 360.0;
    return d < 0 ? d + 360.0 : d;
  }

  /** The angle {@link #spin} has accumulated, in degrees within {@code [0, 360)}. */
  double spinDegrees() {
    return spinDegrees;
  }
}
