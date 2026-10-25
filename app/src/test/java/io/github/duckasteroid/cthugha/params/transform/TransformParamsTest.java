package io.github.duckasteroid.cthugha.params.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

class TransformParamsTest {

  private static final float EPS = 1e-4f;

  private static Vector4f apply(TransformParams transform, float aspect, float x, float y) {
    Matrix4f matrix = transform.applyTo(new Matrix4f(), aspect);
    Vector4f v = new Vector4f(x, y, 0f, 1f);
    matrix.transform(v);
    return v;
  }

  // Regression test for issue #19 item 2: XYParam.is() used anyMatch, so changing only one axis
  // away from identity (while the other stayed at its default) was silently ignored because the
  // *other* axis still matched the identity predicate.

  @Test
  void translateYAloneTakesEffect() {
    TransformParams transform = new TransformParams("Transform");
    transform.translate.y.setValue(0.5);

    Vector4f v = apply(transform, 1f, 0f, 0f);
    assertEquals(0.5f, v.y, EPS);
  }

  @Test
  void stretchYAloneTakesEffect() {
    TransformParams transform = new TransformParams("Transform");
    transform.stretch.y.setValue(2.0);

    Vector4f v = apply(transform, 1f, 0f, 1f);
    assertEquals(2.0f, v.y, EPS);
  }

  @Test
  void shearYAloneTakesEffect() {
    TransformParams transform = new TransformParams("Transform");
    transform.shear.y.setValue(1.0);

    Vector4f v = apply(transform, 1f, 1f, 0f);
    assertEquals(1.0f, v.y, EPS);
  }

  // Composition order: translate is applied last (world-positioning), so it must not be amplified
  // by a scale the user dialled in on top of it.

  @Test
  void stretchDoesNotAmplifyTranslate() {
    TransformParams transform = new TransformParams("Transform");
    transform.translate.x.setValue(0.5);
    transform.stretch.x.setValue(2.0);

    Vector4f v = apply(transform, 1f, 0f, 0f);
    assertEquals(0.5f, v.x, EPS);
  }

  // Rotation must be aspect-corrected so a 90-degree turn preserves physical distance from the
  // pivot rather than squishing to the narrower axis of a non-square viewport.

  @Test
  void rotationIsAspectCorrected() {
    TransformParams transform = new TransformParams("Transform");
    transform.rotation.setValue(90.0);

    Vector4f v = apply(transform, 2f, 1f, 0f);
    assertEquals(0f, v.x, EPS);
    assertEquals(2f, v.y, EPS);
  }

  @Test
  void rotationAtUnitAspectMatchesPlainRotation() {
    TransformParams transform = new TransformParams("Transform");
    transform.rotation.setValue(90.0);

    Vector4f v = apply(transform, 1f, 1f, 0f);
    assertEquals(0f, v.x, EPS);
    assertEquals(1f, v.y, EPS);
  }

  // Zoom multiplies both stretch axes, and maps geometrically so 1x is mid-range.

  @Test
  void zoomMultipliesStretch() {
    TransformParams transform = new TransformParams("Transform");
    transform.zoom.setValue(2.0);
    transform.stretch.x.setValue(1.5);

    Vector4f v = apply(transform, 1f, 1f, 1f);
    assertEquals(3.0f, v.x, EPS);
    assertEquals(2.0f, v.y, EPS);
  }

  @Test
  void zoomBindingMidpointIsNormalSize() {
    TransformParams transform = new TransformParams("Transform");
    transform.zoom.setNormalisedValue(0.5);
    assertEquals(1.0, transform.zoom.value, 1e-9);
    transform.zoom.setNormalisedValue(0.0);
    assertEquals(0.25, transform.zoom.value, 1e-9);
    transform.zoom.setNormalisedValue(1.0);
    assertEquals(4.0, transform.zoom.value, 1e-9);
    // Symmetric: a quarter of the way is half size, three quarters is double.
    transform.zoom.setNormalisedValue(0.25);
    assertEquals(0.5, transform.zoom.value, 1e-9);
  }

  // Spin accumulates from elapsed time, on top of the fixed rotation.

  @Test
  void spinTurnsByElapsedTime() {
    TransformParams transform = new TransformParams("Transform");
    transform.spin.setValue(0.25); // a quarter turn per second
    transform.applyTo(new Matrix4f(), 1f, 0L);
    transform.applyTo(new Matrix4f(), 1f, 100_000_000L);
    assertEquals(9.0, transform.spinDegrees(), 1e-6);
    // Calling twice in a frame (e.g. indexed and screen passes) adds nothing more.
    transform.applyTo(new Matrix4f(), 1f, 100_000_000L);
    assertEquals(9.0, transform.spinDegrees(), 1e-6);
  }

  @Test
  void changingSpinSpeedDoesNotJumpTheAngle() {
    TransformParams transform = new TransformParams("Transform");
    transform.spin.setValue(1.0);
    transform.applyTo(new Matrix4f(), 1f, 0L);
    transform.applyTo(new Matrix4f(), 1f, 100_000_000L);
    double before = transform.spinDegrees();
    transform.spin.setValue(-2.0);
    transform.applyTo(new Matrix4f(), 1f, 100_000_000L);
    assertEquals(before, transform.spinDegrees(), 1e-9);
    transform.applyTo(new Matrix4f(), 1f, 150_000_000L);
    // -2 rev/s for 0.05 s = -36 degrees: back to 0.
    assertEquals(36.0, before, 1e-6);
    assertEquals(0.0, transform.spinDegrees(), 1e-6);
    transform.applyTo(new Matrix4f(), 1f, 175_000_000L);
    // A further -18 degrees wraps into [0, 360).
    assertEquals(342.0, transform.spinDegrees(), 1e-6);
  }

  @Test
  void spinIsCappedAfterAStall() {
    TransformParams transform = new TransformParams("Transform");
    transform.spin.setValue(1.0);
    transform.applyTo(new Matrix4f(), 1f, 0L);
    transform.applyTo(new Matrix4f(), 1f, 5_000_000_000L);
    assertEquals(TransformParams.MAX_SPIN_STEP_SECONDS * 360.0, transform.spinDegrees(), 1e-6);
  }

  @Test
  void spinAddsToRotation() {
    TransformParams transform = new TransformParams("Transform");
    transform.rotation.setValue(45.0);
    transform.spin.setValue(0.125); // 45 degrees per second
    Matrix4f m = null;
    for (long t = 0; t <= 1_000_000_000L; t += 200_000_000L) {
      m = transform.applyTo(new Matrix4f(), 1f, t);
    }
    Vector4f v = new Vector4f(1f, 0f, 0f, 1f);
    m.transform(v);
    assertEquals(0f, v.x, EPS); // 45 + 45 = 90 degrees
    assertEquals(1f, v.y, EPS);
  }

  // Animation packages: modifiers on top of the transform's own values.

  @Test
  void animationsNeverWriteTheTransformsValues() {
    TransformParams transform = new TransformParams("Transform");
    transform.zoom.setValue(1.5);
    transform.rotation.setValue(10.0);
    transform.animations.add(io.github.duckasteroid.cthugha.params.transform.animation.AnimationType.BREATHE);
    transform.animations.add(io.github.duckasteroid.cthugha.params.transform.animation.AnimationType.TUMBLE);
    for (long t = 0; t <= 500_000_000L; t += 16_000_000L) transform.applyTo(new Matrix4f(), 1f, t);
    assertEquals(1.5, transform.zoom.value, 0);
    assertEquals(10.0, transform.rotation.value, 0);
  }

  @Test
  void intensityZeroReturnsExactlyToTheTransformsValues() {
    TransformParams plain = new TransformParams("Transform");
    plain.zoom.setValue(1.5);
    plain.translate.x.setValue(0.2);
    TransformParams animated = new TransformParams("Transform");
    animated.zoom.setValue(1.5);
    animated.translate.x.setValue(0.2);
    var drift = animated.animations.add(io.github.duckasteroid.cthugha.params.transform.animation.AnimationType.DRIFT);
    var breathe = animated.animations.add(io.github.duckasteroid.cthugha.params.transform.animation.AnimationType.BREATHE);
    drift.intensity.setValue(0);
    breathe.intensity.setValue(0);
    for (long t = 0; t <= 300_000_000L; t += 16_000_000L) animated.applyTo(new Matrix4f(), 1f, t);
    assertEquals(plain.applyTo(new Matrix4f(), 1f, 0L), animated.applyTo(new Matrix4f(), 1f, 316_000_000L));
  }

  @Test
  void depthAtZeroTurnKeepsAFlatElementItsNormalSize() {
    TransformParams transform = new TransformParams("Transform");
    var coin = transform.animations.add(io.github.duckasteroid.cthugha.params.transform.animation.AnimationType.COIN_SPIN);
    coin.intensity.setValue(0); // asks for perspective, turns nothing
    Matrix4f m = transform.applyTo(new Matrix4f(), 1f, 0L);
    Vector4f v = new Vector4f(0.5f, -0.25f, 0f, 1f);
    m.transform(v);
    assertEquals(0.5f, v.x / v.w, EPS);
    assertEquals(-0.25f, v.y / v.w, EPS);
  }

  @Test
  void turnRotatesInDepth() {
    TransformParams transform = new TransformParams("Transform");
    transform.perspective.turn.setValue(60.0);
    Matrix4f m = transform.applyTo(new Matrix4f(), 1f, 0L);
    Vector4f v = new Vector4f(0.5f, 0f, 0f, 1f);
    m.transform(v);
    // Turned 60 degrees about the vertical axis: x shrinks to 0.5 cos 60 = 0.25 and the point
    // moves 0.5 sin 60 further from the camera (at 1/tan 30), so it projects to
    // 0.25 × 1.732 / (1.732 + 0.433) = 0.2.
    assertEquals(0.2f, v.x / v.w, 1e-3f);
    assertEquals(0f, v.y / v.w, EPS);
  }
}
