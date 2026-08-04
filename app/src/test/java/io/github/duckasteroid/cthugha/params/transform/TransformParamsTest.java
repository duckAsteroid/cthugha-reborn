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
  void scaleYAloneTakesEffect() {
    TransformParams transform = new TransformParams("Transform");
    transform.scale.y.setValue(2.0);

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
  void scaleDoesNotAmplifyTranslate() {
    TransformParams transform = new TransformParams("Transform");
    transform.translate.x.setValue(0.5);
    transform.scale.x.setValue(2.0);

    Vector4f v = apply(transform, 1f, 0f, 0f);
    assertEquals(0.5f, v.x, EPS);
  }

  // Rotation must be aspect-corrected so a 90-degree turn preserves physical distance from the
  // pivot rather than squishing to the narrower axis of a non-square viewport.

  @Test
  void rotationIsAspectCorrected() {
    TransformParams transform = new TransformParams("Transform");
    transform.rotate.setValue(Math.PI / 2);

    Vector4f v = apply(transform, 2f, 1f, 0f);
    assertEquals(0f, v.x, EPS);
    assertEquals(2f, v.y, EPS);
  }

  @Test
  void rotationAtUnitAspectMatchesPlainRotation() {
    TransformParams transform = new TransformParams("Transform");
    transform.rotate.setValue(Math.PI / 2);

    Vector4f v = apply(transform, 1f, 1f, 0f);
    assertEquals(0f, v.x, EPS);
    assertEquals(1f, v.y, EPS);
  }
}
