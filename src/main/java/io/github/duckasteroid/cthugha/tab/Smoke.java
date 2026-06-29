package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import java.nio.ShortBuffer;
import java.util.Random;
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

  public PixelMapper generate(int width, int height, Random rng) {
    int dvX = directionVectorX.value;
    int dvY = directionVectorY.value;
    return (dstX, dstY, dst, dstOffset, r) -> {
      int map_x = dstX - dvX;
      int map_y = dstY - dvY;
      if (map_y >= height || map_y < 0 || map_x >= width || map_x < 0) {
        map_x = 0;
        map_y = 0;
      }
      dst.put(dstOffset,     (short) map_x);
      dst.put(dstOffset + 1, (short) map_y);
    };
  }

  @Override
  public String toString() {
    return "Smoke{" +
      "speed=" + speed +
      ", randomness=" + randomness +
      '}';
  }
}
