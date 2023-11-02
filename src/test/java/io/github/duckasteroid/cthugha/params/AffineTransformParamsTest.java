package io.github.duckasteroid.cthugha.params;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Dimension;
import java.awt.geom.AffineTransform;
import org.junit.jupiter.api.Test;

class AffineTransformParamsTest {

  AffineTransformParams subject = new AffineTransformParams("Test");
  @Test
  void applyTo() {
    AffineTransform identity = new AffineTransform();
    identity = subject.applyTo(new Dimension(1,1), identity);
    assertTrue(identity.isIdentity());
  }
}
