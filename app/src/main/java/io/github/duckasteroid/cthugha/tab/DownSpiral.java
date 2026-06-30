package io.github.duckasteroid.cthugha.tab;

import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.lang.Math.atan;
import static java.lang.Math.ceil;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;

public class DownSpiral extends AbstractNode implements TranslateTableSource {
  public DoubleParameter a = new DoubleParameter("A", 0, Float.MAX_VALUE, 0.75);
  public DoubleParameter b = new DoubleParameter("B", 1, Float.MAX_VALUE, 1500);

  public DownSpiral() {
    super("Down spiral tab");
    initChildren(a,b);
  }

  @Override
  public PixelMapper generate(int width, int height, Random rng) {
    int cx = width / 2;
    int cy = height / 2;
    float q = 3.14159265399f / 2;
    float p = (float) (PI / 4);
    double av = a.value;
    double bv = b.value;
    int area = width * height;
    return (dstX, dstY, dst, dstOffset, r) -> {
      int dx, dy;
      if (dstY == 0 || dstY == height - 1) {
        dx = (int)((cx - dstX) * av);
        dy = cy - dstY;
      } else {
        int dist = (int) sqrt((dstX - cx) * (dstX - cx) + (dstY - cy) * (dstY - cy));
        float ang;
        if (dstX == cx) {
          ang = dstY > cy ? q : -q;
        } else {
          ang = (float) atan((float)(dstY - cy) / (dstX - cx));
        }
        if (dstX < cx) ang += PI;
        dx = (int) ceil(-sin(ang - p) * dist / bv);
        dy = (int) ceil( cos(ang - p) * dist / bv);
        if (dstX == 0 || dstX == width - 1) {
          dx = cx - dstX;
          dy = (int)((cy - dstY) * av);
        }
      }
      int flat = abs(dstX + dx + (dstY + dy) * width) % area;
      dst.put(dstOffset,     (short)(flat % width));
      dst.put(dstOffset + 1, (short)(flat / width));
    };
  }
}
