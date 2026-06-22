package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;

public class Smoke extends AbstractNode implements TranslateTableSource {

  public IntegerParameter speed = new IntegerParameter("Speed", 1, 300); // 30 - 300
  public IntegerParameter randomness = new IntegerParameter("Randomness", 1, 100); // 0 - 100
  public IntegerParameter directionVectorX = new IntegerParameter("x", -10, 10, -6);
  public IntegerParameter directionVectorY = new IntegerParameter("y", -10, 10, -4);

  public Smoke() {
    this(100,70);
  }

  public Smoke(int speed, int randomness) {
    super("Smoke tab");
    this.speed.setValue( speed);
    this.randomness.setValue(randomness);
    initChildren(this.speed, this.randomness, directionVectorX, directionVectorY);
  }

  public int[] generate(int width, int height) {
    int map_x, map_y;
    int[] directionVector = new int[] {directionVectorX.value, directionVectorY.value};

    int[] result = new int[width * height];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        map_x = x - (directionVector[0]);
        map_y = y - (directionVector[1]);

        if (map_y >= height || map_y < 0  ||
          map_x >= width  || map_x < 0 ) {
          map_x = 0;
          map_y = 0;
        }

        result[x + y * width] = map_y * width + map_x;

      }
    }
    return result;
  }

  @Override
  public String toString() {
    return "Smoke{" +
      "speed=" + speed +
      ", randomness=" + randomness +
      '}';
  }
}
