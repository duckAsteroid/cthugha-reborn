package io.github.duckasteroid.cthugha.animation;

/**
 * Linearly slides to and fro between 0 and 1
 */
public class LinearAnimator extends Animator {
  public double stepSize;
  public double value;

  public LinearAnimator(double initialValue, double stepSize) {
    if (stepSize <= 0) throw new IllegalArgumentException("step must be > 0");
    if (stepSize >= 1) throw new IllegalArgumentException("step must be < 1");
    this.stepSize = stepSize;
    this.value = initialValue;
  }

  @Override
  public double next() {
    double tmp = value += stepSize;
    if (tmp > 1.0) {
      tmp = 1.0;
      stepSize = -1.0 * stepSize;
    }
    else if (tmp < 0.0) {
      tmp = 0.0;
      stepSize = -1.0 * stepSize;
    }
    return tmp;
  }
}
