package io.github.duckasteroid.cthugha.tab;

import java.awt.Dimension;
import java.util.Random;

/**
 * Interface to an object that produces pixel translation tables
 */
public interface TranslateTableSource {

  /**
   * Generate this translation table at the given screen size
   * @param size
   * @return
   */
  int[] generate(Dimension size);

  /**
   * Randomise the generating parameters of this translation
   */
  void randomise();

  Random random = new Random();
  /**
   * A helper method for the child implementations - does what C++ Random function does
   * @param range
   * @return
   */
  default int Random(int range) {
    if (range > 0) {
      return random.nextInt(range);
    }
    return 0;
  }
}
