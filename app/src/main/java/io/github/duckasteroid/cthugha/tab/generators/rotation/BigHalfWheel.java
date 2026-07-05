package io.github.duckasteroid.cthugha.tab.generators.rotation;

import io.github.duckasteroid.cthugha.tab.*;
import com.google.auto.service.AutoService;

import static java.lang.Math.PI;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.transform.XYParam;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;

@AutoService(TabGenerator.class)
public class BigHalfWheel extends AbstractNode implements TabGenerator {
  public XYParam center = new XYParam("Wheel centre location", 0, 1, 0.4, 0.0);

  public BigHalfWheel() {
    super("Big half wheel");
    initChildren(center);
    withResetAction();
  }

  @Override
  public TabMapping generate(int width, int height, Random rng) {
    int cx = (int)(width  * center.x.value);
    int cy = (int)(height * center.y.value);
    float q = (float)(PI / 2);
    float p = (float)(PI / 4);
    int area = width * height;
    return (dstX, dstY, dst, dstOffset, r) -> {
      int dx, dy;
      if (dstY == 0 || dstY == height - 1) {
        dx = (int)((float)(cx - dstX) * 0.75);
        dy = cy - dstY;
      } else {
        int dist = (int)Math.sqrt((dstX - cx) * (dstX - cx) + (dstY - cy) * (dstY - cy));
        float ang;
        if (dstX == cx) {
          ang = dstY > cy ? q : -q;
        } else {
          ang = (float)Math.atan((float)(dstY - cy) / (dstX - cx));
        }
        if (dstX < cx) ang += PI;
        if (dist < height) {
          dx = (int)Math.ceil(-Math.sin(ang - p) * dist / 10.0);
          dy = (int)Math.ceil( Math.cos(ang - p) * dist / 10.0);
        } else {
          dx = dstX < cx ? 3 : -3;
          dy = 0;
        }
        if (dstX == 0 || dstX == width - 1) {
          dx = cx - dstX;
          dy = (int)((float)(cy - dstY) * 0.75);
        }
      }
      int flat = Math.abs(dstX + dx + (dstY + dy) * width) % area;
      dst.put(dstOffset,     (short)(flat % width));
      dst.put(dstOffset + 1, (short)(flat / width));
    };
  }
}
