package io.github.duckasteroid.cthugha.params.animation;


import io.github.duckasteroid.cthugha.params.AbstractValue;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.numbers.fraction.Fraction;

/**
 * A thing that updates parameters
 */
public abstract class Animator {

  public static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();
  protected final BigDecimal animationTime;
  private final List<AbstractValue> targets = Collections.synchronizedList(new ArrayList<>());

  protected Animator(Duration animationTime) {
    this.animationTime = asDecimal(animationTime);
  }

  public Duration getAnimationTime() {
    return Duration.ofNanos(animationTime.multiply(BigDecimal.valueOf(NANOS_PER_SECOND)).longValue());
  }

  /**
   * Creates a big decimal view of the duration in seconds
   * Including the nanosecond part as 10<sup>-9</sup>
   * @param d the duration
   * @return the big decimal second value
   */
  protected static BigDecimal asDecimal(Duration d) {
    return BigDecimal.valueOf(d.getSeconds()).add(BigDecimal.valueOf(d.getNano(), 9));
  }

  public void addTarget(AbstractValue param) {
    targets.add(param);
  }

  public void removeTarget(AbstractValue param) {
    targets.remove(param);
  }

  public void doAnimation(Duration clock) {
    BigDecimal animationFraction = animationFraction(clock);
    final Fraction fract = next(animationFraction);
    targets.forEach(target -> target.setValue(fract));
  }

  public BigDecimal animationFraction(Duration clock) {
    BigDecimal dbClock = asDecimal(clock);
    BigDecimal remainder = dbClock.remainder(animationTime);
    return remainder.divide(animationTime, RoundingMode.HALF_EVEN);
  }

  public abstract Fraction next(BigDecimal animationFraction);
}
