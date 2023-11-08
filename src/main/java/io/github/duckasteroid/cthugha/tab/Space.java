package io.github.duckasteroid.cthugha.tab;

import static java.lang.Math.abs;

import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;
import io.github.duckasteroid.cthugha.params.values.LongParameter;
import java.awt.Dimension;

public class Space extends TranslateTableSource {

  public BooleanParameter reverse = new BooleanParameter("Reverse", true);
  public IntegerParameter randomness = new IntegerParameter("Randomness", 0, 250);
  public LongParameter speed = new LongParameter("Speed", 1, 100, 2);

  @Override
  public void randomiseParameters() {
    reverse.randomise();
    speed.randomise();
    randomness.randomise();
  }

  @Override
  public int[] generate(Dimension size) {
    int map_x, map_y;
    int[] result = new int[size.width * size.height];

    for (int y = 0; y < size.height; y++) {
      for (int x = 0; x < size.width; x++) {
        int dx = x - (size.width / 2);
        int dy = y - (size.height / 2);

        if (!reverse.value && abs(dx) < 30 && abs(dy) < 20 &&
          Random(abs(dx) + abs(dy)) < 4) {
          map_x = Random(size.width);
          map_y = Random(size.height);
        } else {
          long sp = speed.value;

          if (reverse.value)
            sp = (-sp);

          map_x = (int) (x - (dx * sp) / 700);
          map_y = (int) (y - (dy * sp) / 700);
        }

        if (map_y >= size.height || map_y < 0 ||
          map_x >= size.width || map_x < 0) {
          map_x = 0;
          map_y = 0;
        }

        result[x + y * size.width] = map_y * size.width + map_x;

      }
    }
    return result;
  }

  @Override
  public String toString() {
    return "Space{" +
      reverse + randomness + speed +
      '}';
  }
}
