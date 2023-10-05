package io.github.duckasteroid.cthugha.animation;

public class SigmoidAnimator extends Animator {
  private final double slope;
  private final double rate;
  private final LinearAnimator value;

  public SigmoidAnimator(double slope, double rate, double stepSize) {
    this.slope = slope;
    this.rate = rate;
    this.value = new LinearAnimator(0, stepSize);
  }

  public double sigmoid(double x) {
    return 1 / (1 + Math.exp(-slope * (x - rate)));
  }

  @Override
  public double next() {
    double x = value.next();
    return sigmoid(x);
  }
}
