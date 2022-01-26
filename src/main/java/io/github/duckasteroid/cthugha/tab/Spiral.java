package io.github.duckasteroid.cthugha.tab;

import static java.lang.Math.abs;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Random;

public class Spiral implements TranslateTableSource{
  Random rnd = new Random();
  /** number of spirals (0 for one centered spiral) */
  int nr_spirals = 1;
  /** change of radius (0 -> simple rotation) */
  double delta_r=2.0;
  /** change of angle (default 0.1) */
  double delta_a=0.1;
  /** Does the direction of rotation change with the radius. */
  boolean yinyang = false;
  /** Width of section of constant direction (if changing dir.) */
  double yywidth = 10.0;

  private static final int MAX_NR_SPIRALS = 64;

  public Spiral() {}

  public Spiral(int nr_spirals, double delta_r, double delta_a, boolean yinyang, double yywidth) {
    this.nr_spirals = nr_spirals;
    this.delta_r = delta_r;
    this.delta_a = delta_a;
    this.yinyang = yinyang;
    this.yywidth = yywidth;
  }

  public Spiral numSpirals(int nr_spirals) {
    this.nr_spirals = nr_spirals;
    return this;
  }

  public Spiral deltaR(double delta_r) {
    this.delta_r = delta_r;
    return this;
  }

  public Spiral deltaA(double delta_a) {
    this.delta_a = delta_a;
    return this;
  }

  public Spiral yinyang(int yywidth) {
    this.yinyang = true;
    this.yywidth = yywidth;
    return this;
  }

  private final int rand() {
    return rnd.nextInt(Short.MAX_VALUE);
  }

  @Override
  public int[] generate(Dimension size) {

    ArrayList<Integer> result = new ArrayList<>(size.width * size.height);
    int[] centersX = new int[MAX_NR_SPIRALS];
    int[] centersY = new int[MAX_NR_SPIRALS];
    int[] dir = new int[MAX_NR_SPIRALS];

    if(nr_spirals == 0) {
      centersX[0] = size.width / 2;
      centersY[0] = size.height / 2;
      dir[0] = 1;
      nr_spirals = 1;
    } else {
      nr_spirals=max(min(nr_spirals, MAX_NR_SPIRALS),1);
      for (int i=0; i<nr_spirals; i++) {
        centersX[i]=rand() % size.width;
        centersY[i]=rand() % size.height;
        dir[i]=rnd.nextBoolean() ? 1 : -1;
      }
    }

    int closest;
    double dist;
    double polar_r,polar_a;

    double temp_y,temp_x;
    double cent_y,cent_x;
    long l = rand();

    for (int y=0; y<size.height; y++) {

      for (int x=0; x<size.width; x++) {
        closest=0;
        dist=9999999.0;

        temp_x=x;
        temp_y=y;
        for (int i=0; i<nr_spirals; i++) {
          if(dist>(sqrt((temp_x-centersX[i])*(temp_x-centersX[i])+
            (temp_y-centersY[i])*(temp_y-centersY[i])))){
            closest=i;
            dist=sqrt((temp_x-centersX[i])*(temp_x-centersX[i])+
              (temp_y-centersY[i])*(temp_y-centersY[i]));
          }
        }
        int mapValue ;
        if ((x==centersX[closest]) && (y==centersY[closest])) {
          mapValue = 0;
        } else {
          cent_y=centersY[closest];
          cent_x=centersX[closest];
          temp_x=abs(x-cent_x);
          temp_y=abs(y-cent_y);

          polar_r=sqrt(temp_x*temp_x + temp_y*temp_y);
          polar_a=atan2(x-cent_x, y-cent_y);

          polar_r += (delta_r+(rand()%10)*0.01)*(double)dir[closest];

          if (polar_r<0)
            polar_r=0.0;

          if ( yinyang ) {

            polar_a -= delta_a * 3.0 *
              (float)(5-(int)(polar_r/11) % 11)/5.0;

            if (((int)(polar_r/yywidth)%2) != 0) {
              polar_a += delta_a;
            } else {
              polar_a -= delta_a;
            }
          } else {

            polar_a += (delta_a+(rand()%10)*0.01)
              *(double)dir[closest];
          }

          temp_y=polar_r*(cos(polar_a));
          temp_x=polar_r*(sin(polar_a));

          int map_x=(int)(temp_x+cent_x);
          int map_y=(int)(temp_y+cent_y);

          if ((map_y>=size.height) || (map_y<0) ||
            (map_x>=size.width) || (map_x<0) ) {
            map_x=0;
            map_y=0;
          }

          map_x=max(map_x,0);
          map_y=max(map_y,0);

          mapValue = map_y * size.width + map_x;
        }
        result.add(mapValue);
      }
    }
    return result.stream().mapToInt(Integer::intValue).toArray();
  }
}
