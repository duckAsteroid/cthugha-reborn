package io.github.duckasteroid.cthugha.animation;

/**
 * Follows a sine wave
 */
public class SineAnimator extends Animator {
  public double stepSize;
  public double angle;

  public SineAnimator(double initialAngle, double stepSize) {
    if (stepSize < 0) throw new IllegalArgumentException("step must be > 0");
    this.stepSize = stepSize;
    this.angle = initialAngle;
  }

  @Override
  public double next() {
    angle += stepSize;
    if (angle > 360) {
      angle = 0;
    }
    return Math.sin(Math.toRadians(angle));
  }
}
