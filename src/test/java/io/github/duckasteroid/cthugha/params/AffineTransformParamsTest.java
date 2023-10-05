package io.github.duckasteroid.cthugha.params;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.geom.AffineTransform;
import org.junit.jupiter.api.Test;

class AffineTransformParamsTest {

  AffineTransformParams subject = new AffineTransformParams("Test");
  @Test
  void applyTo() {
    AffineTransform identity = new AffineTransform();
    identity = subject.applyTo(identity);
    assertTrue(identity.isIdentity());
  }
}
