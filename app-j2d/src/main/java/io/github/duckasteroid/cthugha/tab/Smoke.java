package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.display.ScreenBuffer;
import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;
import java.awt.Dimension;

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


  public int[] generate(ScreenBuffer buffer) {
    final Dimension size = buffer.getDimensions();
    int map_x, map_y;
    int[] directionVector = new int[] {directionVectorX.value, directionVectorY.value};

    int[] result = new int[size.width * size.height];
    for (int y = 0; y < size.height; y++) {
      for (int x = 0; x < size.width; x++) {
        map_x = x - (directionVector[0]); //+ rnd.nextInt( randomness.value / 100)) * speed.value / 100;
        map_y = y - (directionVector[1]);// + rnd.nextInt( randomness.value / 100)) * speed.value / 300;

        if (map_y >= size.height || map_y < 0  ||
          map_x >= size.width  || map_x < 0 ) {
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
    return "Smoke{" +
      "speed=" + speed +
      ", randomness=" + randomness +
      '}';
  }
}