package io.github.duckasteroid.cthugha.tab;

import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.lang.Math.atan;
import static java.lang.Math.ceil;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;

public class DownSpiral extends AbstractNode implements TranslateTableSource {
  public DoubleParameter a = new DoubleParameter("A", 0, Float.MAX_VALUE, 0.75);
  public DoubleParameter b = new DoubleParameter("B", 1, Float.MAX_VALUE, 1500);

  public DownSpiral() {
    super("Down spiral tab");
    initChildren(a,b);
  }

  @Override
  public int[] generate(int width, int height) {
    int[] theTab = new int[width * height];
    int dx, dy, dist;
    int cx = width / 2;
    int cy = height / 2;
    float q = 3.14159265399f / 2;
    float p = (float) (45 / 180 * PI);
    float ang;

    for (int j = 0; j < height; j++) {

      for (int i = 0; i < width; i++) {

        if (j == 0 || j == height) {

          dx = (int) ((double) (cx - i) * a.value);
          dy = cy - j;

        } else {

          dist = (int) sqrt((i - cx) * (i - cx) + (j - cy) * (j - cy));

          if (i == cx) {
            if (j > cx)
              ang = q;
            else
              ang = -q;
          } else
            ang = (float) atan((float) (j - cy) / (i - cx));

          if (i < cx)
            ang += PI;

          dx = (int) ceil(-sin(ang - p) * dist / b.value);
          dy = (int) ceil(cos(ang - p) * dist / b.value);

          if (i == 0 || i == width) {
            dx = cx - i;
            dy = (int) ((float) (cy - j) * a.value);
          }
        }

        theTab[i + j * width] = abs(i + dx + ((j + dy) * width)) % theTab.length;

      }
    }
    return theTab;
  }
}
