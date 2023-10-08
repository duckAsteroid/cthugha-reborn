package io.github.duckasteroid.cthugha.params.animation;

import io.github.duckasteroid.cthugha.params.Fraction;
import java.math.BigDecimal;
import java.time.Duration;

/**
 * Linearly slides to and fro between 0 and 1
 */
public class LinearAnimator extends Animator {
  private final RampType rampType;

  public enum RampType {
    RAMP_UP, RAMP_DOWN
  }
  private final Fraction initialValue;
  public LinearAnimator(Duration time) {
    this(Fraction.ZERO, time, RampType.RAMP_UP);
  }

  public LinearAnimator(double fraction, Duration time) {
    this(new Fraction(fraction), time, RampType.RAMP_DOWN);
  }
  public LinearAnimator(Fraction initialValue, Duration stepSize, RampType rampType) {
    super(stepSize);
    this.rampType = rampType;
    this.initialValue = initialValue;
  }

  @Override
  public Fraction next(BigDecimal animationFraction) {
    double value = animationFraction.doubleValue();
    if (rampType == RampType.RAMP_DOWN) {
        value = 1.0 / animationFraction.doubleValue();
    }
    return new Fraction(value);
  }
}
