package io.github.duckasteroid.cthugha.tab;

import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.lang.Math.atan;
import static java.lang.Math.ceil;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import java.awt.Dimension;

public class DownSpiral extends TranslateTableSource {
  float a = 0.75f;
  float b = 10.0f;

  @Override
  public void randomiseParameters() {
    a = rnd.nextFloat();
    b = rnd.nextFloat() * 150;
  }

  @Override
  public int[] generate(Dimension size) {
    int[] theTab = new int[size.width * size.height];
    int dx, dy, dist;
    int cx = size.width / 2;
    int cy = size.height / 2;
    float q = 3.14159265399f / 2;
    float p = (float) (45 / 180 * PI);
    float ang;

    for (int j = 0; j < size.height; j++) {

      for (int i = 0; i < size.width; i++) {

        if (j == 0 || j == size.height) {

          dx = (int) ((float) (cx - i) * a);
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

          dx = (int) ceil(-sin(ang - p) * dist / b);
          dy = (int) ceil(cos(ang - p) * dist / b);

          if (i == 0 || i == size.width) {
            dx = cx - i;
            dy = (int) ((float) (cy - j) * a);
          }
        }

        theTab[i + j * size.width] = abs(i + dx + ((j + dy) * size.width)) % theTab.length;

      }
    }
    return theTab;
  }

  @Override
  public String toString() {
    return "DownSpiral{" +
      "a=" + a +
      ", b=" + b +
      '}';
  }
}
