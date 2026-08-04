package io.github.duckasteroid.cthugha.params.transform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class XYParamTest {

  private static final double DP = 1e-9;

  private static boolean isZero(double v) {
    return Math.abs(v) < DP;
  }

  @Test
  void isTrueOnlyWhenBothComponentsMatch() {
    XYParam xy = new XYParam("Test", -1, 1, 0);
    assertTrue(xy.is(XYParamTest::isZero));

    xy.x.setValue(0.5);
    assertFalse(xy.is(XYParamTest::isZero));
  }

  @Test
  void isFalseWhenOnlyOneComponentMatches() {
    XYParam xy = new XYParam("Test", -1, 1, 0);
    xy.x.setValue(0.5);
    xy.y.setValue(0.0);

    // y alone matching the predicate must not make the pair read as "identity" — regression for
    // issue #19 (single-axis scale/translate changes being silently ignored).
    assertFalse(xy.is(XYParamTest::isZero));
  }
}
