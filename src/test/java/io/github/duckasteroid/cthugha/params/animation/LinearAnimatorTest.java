package io.github.duckasteroid.cthugha.params.animation;

import static org.junit.jupiter.api.Assertions.*;

import io.github.duckasteroid.cthugha.animation.LinearAnimator;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LinearAnimatorTest {

  private LinearAnimator subject = new LinearAnimator(0.5, Duration.ofSeconds(100));
  @Test
  void testAnimationFraction() {
    BigDecimal animationFraction = subject.animationFraction(Duration.ofSeconds(10));
    assertEquals(0.1, animationFraction.doubleValue(), 0.00001);

    animationFraction = subject.animationFraction(Duration.ofSeconds(50));
    assertEquals(0.5, animationFraction.doubleValue(), 0.00001);

    animationFraction = subject.animationFraction(Duration.ofSeconds(100));
    assertEquals(0.0, animationFraction.doubleValue(), 0.00001);

    animationFraction = subject.animationFraction(Duration.ofSeconds(150));
    assertEquals(0.5, animationFraction.doubleValue(), 0.00001);

    animationFraction = subject.animationFraction(Duration.ofSeconds(1051));
    assertEquals(0.51, animationFraction.doubleValue(), 0.00001);
  }
}
