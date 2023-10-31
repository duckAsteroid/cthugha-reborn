package io.github.duckasteroid.cthugha.params.animation;

import io.github.duckasteroid.cthugha.params.Fraction;
import io.github.duckasteroid.cthugha.params.RuntimeParameter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A thing that updates parameters
 */
public abstract class Animator {

  public static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();
  protected final BigDecimal animationTime;
  private final List<RuntimeParameter> targets = Collections.synchronizedList(new ArrayList<>());

  protected Animator(Duration animationTime) {
    this.animationTime = asDecimal(animationTime);
  }

  public Duration getAnimationTime() {
    return Duration.ofNanos(animationTime.multiply(BigDecimal.valueOf(NANOS_PER_SECOND)).longValue());
  }

  protected static BigDecimal asDecimal(Duration d) {
    return BigDecimal.valueOf(d.getSeconds()).add(BigDecimal.valueOf(d.getNano(), 9));
  }

  public void addTarget(RuntimeParameter param) {
    targets.add(param);
  }

  public void removeTarget(RuntimeParameter param) {
    targets.remove(param);
  }

  public void doAnimation(Duration clock) {
    BigDecimal animationFraction = animationFraction(clock);
    final Fraction fract = next(animationFraction);
    targets.forEach(target -> target.setValue(fract));
  }

  BigDecimal animationFraction(Duration clock) {
    BigDecimal dbClock = asDecimal(clock);
    BigDecimal remainder = dbClock.remainder(animationTime);
    return remainder.divide(animationTime, RoundingMode.HALF_EVEN);
  }

  public abstract Fraction next(BigDecimal animationFraction);
}
