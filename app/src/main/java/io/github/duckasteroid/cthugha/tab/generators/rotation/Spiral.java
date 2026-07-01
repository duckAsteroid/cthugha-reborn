package io.github.duckasteroid.cthugha.tab.generators.rotation;

import io.github.duckasteroid.cthugha.tab.*;
import com.google.auto.service.AutoService;

import static java.lang.Math.abs;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;
import java.nio.ShortBuffer;
import java.util.Random;

@AutoService(TabGenerator.class)
public class Spiral extends AbstractNode implements TabGenerator{

  /** number of spirals (0 for one centered spiral) */
  public IntegerParameter nr_spirals = new IntegerParameter("Number of spirals", 0, MAX_NR_SPIRALS, 1);
  /** change of radius (0 -> simple rotation) */
  public DoubleParameter delta_r= new DoubleParameter("Delta R", 0, 5, 2.0);
  /** change of angle (default 0.1) */
  public DoubleParameter delta_a = new DoubleParameter("Delta A", 0, 1, 0.1);
  /** Does the direction of rotation change with the radius. */
  public BooleanParameter yinyang = new BooleanParameter("Yin/Yang");
  /** Width of section of constant direction (if changing dir.) */
  public DoubleParameter yywidth = new DoubleParameter("YY Width", 0, 10.0, 10);


  public Spiral() {
    super("Spirals");
    initChildren(nr_spirals, delta_r, delta_a, yinyang, yywidth);
  }

  private static final int MAX_NR_SPIRALS = 64;

  @Override
  public TabMapping generate(int width, int height, Random rng) {
    int[] centersX = new int[MAX_NR_SPIRALS];
    int[] centersY = new int[MAX_NR_SPIRALS];
    int[] dir = new int[MAX_NR_SPIRALS];
    final int nSpirals;
    if (nr_spirals.value == 0) {
      centersX[0] = width / 2;
      centersY[0] = height / 2;
      dir[0] = 1;
      nSpirals = 1;
    } else {
      nSpirals = max(min(nr_spirals.value, MAX_NR_SPIRALS), 1);
      for (int i = 0; i < nSpirals; i++) {
        centersX[i] = rng.nextInt(Short.MAX_VALUE) % width;
        centersY[i] = rng.nextInt(Short.MAX_VALUE) % height;
        dir[i] = rng.nextBoolean() ? 1 : -1;
      }
    }
    double dr = delta_r.value;
    double da = delta_a.value;
    boolean yy = yinyang.value;
    double yyw = yywidth.value;
    return (dstX, dstY, dst, dstOffset, r) -> {
      int closest = 0;
      double dist = 9999999.0;
      for (int i = 0; i < nSpirals; i++) {
        double d = sqrt((double)(dstX - centersX[i]) * (dstX - centersX[i]) +
                        (double)(dstY - centersY[i]) * (dstY - centersY[i]));
        if (d < dist) { dist = d; closest = i; }
      }
      int map_x, map_y;
      if (dstX == centersX[closest] && dstY == centersY[closest]) {
        map_x = 0; map_y = 0;
      } else {
        double cent_x = centersX[closest];
        double cent_y = centersY[closest];
        double tx = abs(dstX - cent_x);
        double ty = abs(dstY - cent_y);
        double polar_r = sqrt(tx * tx + ty * ty);
        double polar_a = atan2(dstX - cent_x, dstY - cent_y);
        polar_r += (dr + r.nextInt(10) * 0.01) * dir[closest];
        if (polar_r < 0) polar_r = 0.0;
        if (yy) {
          polar_a -= da * 3.0 * (5 - (int)(polar_r / 11) % 11) / 5.0;
          polar_a += ((int)(polar_r / yyw) % 2) != 0 ? da : -da;
        } else {
          polar_a += (da + r.nextInt(10) * 0.01) * dir[closest];
        }
        map_x = max((int)(polar_r * sin(polar_a) + cent_x), 0);
        map_y = max((int)(polar_r * cos(polar_a) + cent_y), 0);
        if (map_y >= height || map_x >= width) { map_x = 0; map_y = 0; }
      }
      dst.put(dstOffset,     (short) map_x);
      dst.put(dstOffset + 1, (short) map_y);
    };
  }
}
