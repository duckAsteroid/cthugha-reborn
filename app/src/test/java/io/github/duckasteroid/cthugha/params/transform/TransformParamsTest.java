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
}
