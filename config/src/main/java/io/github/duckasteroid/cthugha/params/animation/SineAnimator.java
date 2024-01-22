package io.github.duckasteroid.cthugha.params.animation;

import java.math.BigDecimal;
import java.time.Duration;
import org.apache.commons.numbers.fraction.Fraction;

/**
 * Follows a sine wave
 */
public class SineAnimator extends Animator {
  private static final double FULL_SCALE = 2 * Math.PI;
  private final double initalAngle;

  public SineAnimator(double initialAngle, Duration time) {
    super(time);
    this.initalAngle = initialAngle;
  }

  @Override
  public Fraction next(BigDecimal animationFraction) {
    double sin = Math.sin(initalAngle + (animationFraction.doubleValue() * FULL_SCALE));
    return Fraction.of((int)(1 + sin), 2);
  }
}
