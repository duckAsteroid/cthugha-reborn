package io.github.duckasteroid.cthugha.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A thing that updates
 */
public abstract class Animator {

  private final List<Consumer<Double>> targets = new ArrayList<>();

  public void addTarget(Consumer<Double> animatable) {
    targets.add(animatable);
  }

  public void removeTarget(Consumer<Double> animatable) {
    targets.remove(animatable);
  }

  public void doAnimation() {
    double next = next();
    targets.forEach(tgt -> tgt.accept(next));
  }

  public abstract double next();
}
