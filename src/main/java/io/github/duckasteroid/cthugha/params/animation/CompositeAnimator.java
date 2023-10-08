package io.github.duckasteroid.cthugha.params.animation;

import io.github.duckasteroid.cthugha.params.Fraction;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class CompositeAnimator extends Animator {
  private final List<Animator> children;

  protected CompositeAnimator(List<Animator> children) {
    super(Duration.ofNanos(children.stream().map(Animator::getAnimationTime).mapToLong(Duration::toNanos).sum()));
    this.children = Collections.unmodifiableList(children);
  }

  @Override
  public Fraction next(BigDecimal animationFraction) {
    int index = (int)(children.size() * animationFraction.doubleValue());
    return null;
  }
}
