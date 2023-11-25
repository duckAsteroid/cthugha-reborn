package io.github.duckasteroid.cthugha.tab;

import static java.lang.Math.abs;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import io.github.duckasteroid.cthugha.display.ScreenBuffer;
import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Random;

public class Spiral extends AbstractNode implements TranslateTableSource{

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

  private final int rand() {
    return random.nextInt(Short.MAX_VALUE);
  }

  @Override
  public int[] generate(ScreenBuffer buffer) {
    final Dimension size = buffer.getDimensions();

    ArrayList<Integer> result = new ArrayList<>(size.width * size.height);
    int[] centersX = new int[MAX_NR_SPIRALS];
    int[] centersY = new int[MAX_NR_SPIRALS];
    int[] dir = new int[MAX_NR_SPIRALS];

    if(nr_spirals.value == 0) {
      centersX[0] = size.width / 2;
      centersY[0] = size.height / 2;
      dir[0] = 1;
      nr_spirals.value = 1;
    } else {
      nr_spirals.value=max(min(nr_spirals.value, MAX_NR_SPIRALS),1);
      for (int i=0; i<nr_spirals.value; i++) {
        centersX[i]=rand() % size.width;
        centersY[i]=rand() % size.height;
        dir[i]=random.nextBoolean() ? 1 : -1;
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
        for (int i=0; i<nr_spirals.value; i++) {
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

          polar_r += (delta_r.value+(rand()%10)*0.01)*(double)dir[closest];

          if (polar_r<0)
            polar_r=0.0;

          if ( yinyang.value ) {

            polar_a -= delta_a.value * 3.0 *
              (float)(5-(int)(polar_r/11) % 11)/5.0;

            if (((int)(polar_r/yywidth.value)%2) != 0) {
              polar_a += delta_a.value;
            } else {
              polar_a -= delta_a.value;
            }
          } else {

            polar_a += (delta_a.value+(rand()%10)*0.01)
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
