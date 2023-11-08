package io.github.duckasteroid.cthugha.tab;

import static java.lang.Math.PI;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.XYParam;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import java.awt.Dimension;

public class BigHalfWheel extends AbstractNode implements TranslateTableSource {
  public XYParam center = new XYParam("Wheel centre location", 0,1, 0.4);

  public BigHalfWheel() {
    super("Big half wheel");
    initChildren(center);
  }
  @Override
  public int[] generate(Dimension size) {
    int[] theTab = new int[size.height * size.width];
    int dx,dy,cx,cy,dist;
    float q, ang, p;

    cx = (int)(size.width * 0.4);
    cy = 0;
    q = (float) (PI / 2);
    p = (float) (0 / 180 * PI);

    for (int j=0;j<size.height;j++) {

      for (int i = 0; i < size.width; i++) {

        if (j == 0 || j == size.height) {

          dx = (int)((float) (cx - i) * 0.75);
          dy = cy - j;

        } else {

          dist = (int)Math.sqrt((i - cx) * (i - cx) + (j - cy) * (j - cy));

          if (i == cx) {
            if (j > cx)
              ang = q;
            else
              ang = -q;
          } else
            ang = (float) Math.atan((float) (j - cy) / (i - cx));

          if (i < cx)
            ang += PI;
          if (dist < size.height) {
            dx = (int)Math.ceil(-Math.sin(ang - p) * dist / 10.0);
            dy = (int)Math.ceil(Math.cos(ang - p) * dist / 10.0);
          } else {
            dx = (int)Math.ceil(-Math.sin(ang + q) * size.height / 20.0);
            dy = (int)Math.ceil(Math.cos(ang + q) * size.height / 20.0);
            if (i < cx)
              dx = 3;
            else
              dx = -3;
            dy = 0;
          }

          if (i == 0 || i == size.width) {
            dx = cx - i;
            dy = (int)((float) (cy - j) * 0.75);
          }
        }

        theTab[i + j * size.width] = Math.abs(i + dx + ((j + dy) * size.width)) % theTab.length;

      }
    }
    return theTab;
  }
}
