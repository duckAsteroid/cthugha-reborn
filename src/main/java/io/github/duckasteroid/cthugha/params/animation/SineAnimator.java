package io.github.duckasteroid.cthugha.params.animation;

import io.github.duckasteroid.cthugha.params.Fraction;
import java.math.BigDecimal;
import java.time.Duration;

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
    return new Fraction((1.0 + sin) / 2.0 );
  }
}
