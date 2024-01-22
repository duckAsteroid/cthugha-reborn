package io.github.duckasteroid.cthugha.params.animation;

import java.math.BigDecimal;
import java.time.Duration;
import org.apache.commons.numbers.fraction.Fraction;

public class SigmoidAnimator extends Animator {
  private final double slope;
  private final double rate;

  public SigmoidAnimator(double slope, double rate, Duration time) {
    super(time);
    this.slope = slope;
    this.rate = rate;
  }

  public double sigmoid(double x) {
    return 1 / (1 + Math.exp(-slope * (x - rate)));
  }

  @Override
  public Fraction next(BigDecimal f) {
    double x = f.doubleValue();
    return Fraction.from(sigmoid(x));
  }
}
