package io.github.duckasteroid.cthugha.animation;

import java.awt.geom.AffineTransform;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ScaledAnimation {
  private final double min;

  private final double scale;

  private final Consumer<Double> target;

  public ScaledAnimation(double min, double max,
                         Consumer<Double> target) {
    this.min = min;
    this.scale = max - min;
    this.target = target;
  }

  public void onAnimate(double fraction) {
    double value = min + (scale * fraction);
    target.accept(value);
  }

}
