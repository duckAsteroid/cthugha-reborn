package io.github.duckasteroid.cthugha.params.values;

import io.github.duckasteroid.cthugha.params.UiHint;

/**
 * A positive {@link DoubleParameter} whose normalised value maps geometrically onto its range:
 * {@code value = min × (max/min)^n}. Equal steps multiply the value by equal factors, so a
 * multiplier with a range symmetric about 1 (e.g. 0.25–4) puts 1 exactly at {@code n = 0.5},
 * and a binding's {@code sine()} swings evenly above and below it. The remote UI slider uses the
 * same mapping ({@link UiHint#SCALE_GEOMETRIC}).
 */
public class GeometricDoubleParameter extends DoubleParameter {

  /**
   * @param description display name
   * @param min         lower bound; must be positive
   * @param max         upper bound; must exceed {@code min}
   * @param value       initial value (also the reset default)
   */
  public GeometricDoubleParameter(String description, double min, double max, double value) {
    super(description, min, max, value);
    if (min <= 0 || max <= min) {
      throw new IllegalArgumentException("geometric range needs 0 < min < max, was " + min + ".." + max);
    }
    withUiHint(UiHint.SCALE, UiHint.SCALE_GEOMETRIC);
  }

  @Override
  public void setNormalisedValue(double normalisedValue) {
    double min = getMin().doubleValue();
    double max = getMax().doubleValue();
    this.value = min * Math.pow(max / min, normalisedValue);
    fireChangeListeners();
  }
}
