package io.github.duckasteroid.cthugha.tab.generators.lens;

import io.github.duckasteroid.cthugha.tab.*;
import com.google.auto.service.AutoService;

import static java.lang.Math.abs;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;
import io.github.duckasteroid.cthugha.params.values.LongParameter;

@AutoService(TabGenerator.class)
public class Space extends AbstractNode implements TabGenerator {

  public BooleanParameter reverse = new BooleanParameter("Reverse", true);
  public IntegerParameter randomness = new IntegerParameter("Randomness", 0, 250);
  public LongParameter speed = new LongParameter("Speed", 1, 100, 2);

  public Space() {
    super("Space");
    initChildren(reverse, randomness, speed);
  }

  @Override
  public TabMapping generate(int width, int height, Random rng) {
    boolean rev = reverse.value;
    int zone = randomness.value / 8;
    long sp = rev ? -speed.value : speed.value;
    int halfW = width / 2;
    int halfH = height / 2;
    return (dstX, dstY, dst, dstOffset, r) -> {
      int dx = dstX - halfW;
      int dy = dstY - halfH;
      int map_x, map_y;
      if (!rev && zone > 0 && abs(dx) < zone && abs(dy) < zone * 2 / 3 &&
          r.nextInt(abs(dx) + abs(dy) + 1) < 4) {
        map_x = r.nextInt(width);
        map_y = r.nextInt(height);
      } else {
        map_x = (int)(dstX - (dx * sp) / 700);
        map_y = (int)(dstY - (dy * sp) / 700);
      }
      if (map_y >= height || map_y < 0 || map_x >= width || map_x < 0) {
        map_x = 0;
        map_y = 0;
      }
      dst.put(dstOffset,     (short) map_x);
      dst.put(dstOffset + 1, (short) map_y);
    };
  }
}
