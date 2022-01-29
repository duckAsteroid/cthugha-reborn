package io.github.duckasteroid.cthugha.tab;

import java.awt.Dimension;
import java.util.Random;

/**
 * Interface to an object that produces pixel translation tables
 */
public abstract class TranslateTableSource {
  protected Random rnd = new Random();

  /**
   * Generate this translation table at the given screen size
   * @param size
   * @return
   */
  public abstract int[] generate(Dimension size);

  /**
   * Randomise the generating parameters of this translation
   */
  public void randomiseParameters() {}

  /**
   * A helper method for the child implementations - does what C++ Random function does
   * @param range
   * @return
   */
  protected int Random(int range) {
    if (range > 0) {
      return rnd.nextInt(range);
    }
    return 0;
  }
}
