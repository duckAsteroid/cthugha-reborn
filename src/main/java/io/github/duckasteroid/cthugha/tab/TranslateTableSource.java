package io.github.duckasteroid.cthugha.tab;

import java.awt.Dimension;
import java.util.Random;

/**
 * Interface to an object that produces pixel translation tables
 */
public interface TranslateTableSource {
  Random rnd = new Random();

  /**
   * Generate this translation table at the given screen size
   * @param size
   * @return
   */
  int[] generate(Dimension size);

  /**
   * A helper method for the child implementations - does what C++ Random function does
   * @param range
   * @return
   */
  static int Random(int range) {
    if (range > 0) {
      return rnd.nextInt(range);
    }
    return 0;
  }
}
