package io.github.duckasteroid.cthugha.params;

/**
 * A number between 0 and 1 inclusive
 */
public class Fraction {
  public static final Fraction ZERO = new Fraction(0.0);
  public static final Fraction ONE = new Fraction(1.0);
  public final double fraction;

  public Fraction(double fraction) {
    if (fraction < 0) throw new IllegalArgumentException("Must be > 0");
    if (fraction > 1) throw new IllegalArgumentException("Must be > 0");
    this.fraction = fraction;
  }

  public Fraction(double numerator, double denominator) {
    this(numerator / denominator);
  }

  public Fraction offset(double other) {
    double value = (this.fraction + other) % ONE.fraction;
    return new Fraction(value);
  }
}
