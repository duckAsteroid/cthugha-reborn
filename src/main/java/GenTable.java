import static java.lang.Math.abs;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Random;
import java.util.concurrent.Callable;

public class GenTable implements Callable<Integer> {
  public static void main(String[] args) throws Exception {
    GenTable gt = new GenTable(args);
    System.exit(gt.call());
  }

  final Random rnd = new Random();

  public final int BUFF_WIDTH; // 320
  public final int BUFF_HEIGHT; //200
  int nr_spirals = 1;
  double delta_r=2.0,delta_a=0.1;
  boolean yinyang = false;
  double yywidth = 10.0;

  private DataOutput map;

  private static final int MAX_NR_SPIRALS = 64;

  int[] centersX = new int[MAX_NR_SPIRALS];
  int[] centersY = new int[MAX_NR_SPIRALS];
  int[] dir = new int[MAX_NR_SPIRALS];

  public GenTable(String [] args) throws FileNotFoundException {
    final String USAGE = "Gentable usage:\n" +
    "\tgentable tabname.tab WIDTH HEIGHT [-]<#spirals> <yywidth> <(float)delta_r> <(float)delta_a>\n" +
    "\tIf the first parameter starts with a '-' then the direction of rotation\n" +
    "\tchanges with the radius.\n" +
    "\t#spirals: number of spirals (0 for one centered spiral) (def: %d)\n" +
    "\tyywidth:  Width of section of constant direction (if changing dir.)\n" +
    "\tdelta_r:  change of radius (0 -> simple rotation) (def: %f)\n" +
    "\tdelta_a:  change of angle (def: %f)\n";
    if (args == null || args.length < 2) {
      System.err.printf(USAGE, nr_spirals, delta_r, delta_a);
      throw new IllegalArgumentException("");
    }
    else {
      map = new DataOutputStream(new FileOutputStream(args[0]));
      BUFF_WIDTH = Integer.parseInt(args[1]);
      BUFF_HEIGHT = Integer.parseInt(args[2]);

      yinyang = args[2].startsWith("-");

      nr_spirals= Integer.parseInt(args[3].substring(yinyang ? 1 : 0));
      if(nr_spirals == 0) {
        centersX[0] = BUFF_WIDTH / 2;
        centersY[0] = BUFF_HEIGHT / 2;
        dir[0] = 1;
        nr_spirals = 1;
      } else {
        nr_spirals=max(min(nr_spirals, MAX_NR_SPIRALS),1);
        for (int i=0; i<nr_spirals; i++) {
          centersX[i]= rnd.nextInt() % BUFF_WIDTH;
          centersY[i]=rnd.nextInt()%BUFF_HEIGHT;
          dir[i]=rnd.nextInt() % 5 - 2;
        }
      }
      yywidth = Integer.parseInt(args[4]);

      delta_r = Double.parseDouble(args[5]);
      delta_a = Double.parseDouble(args[6]);
    }
  }

  @Override
  public Integer call() throws Exception {
    int x,y,map_x,map_y,i;
    int closest;
    double dist;
    double polar_r,polar_a;

    double temp_y,temp_x;
    double cent_y,cent_x;
    long l;



/*
    printf("%s %s\n", *(argv+1), *(argv+2));
*/





/*
  if( yinyang)
  printf("Writing mapping table: %s\n"
  "  #spirals: %d  yywidth: %f  delta_a: %f  delta_r: %f\n" ,
  maptabfile, nr_spirals, yywidth, delta_a, delta_r);
  else
  printf("Writing mapping table: %s\n"
  "  #spirals: %d  delta_a: %f  delta_r: %f\n" ,
  maptabfile, nr_spirals, delta_a, delta_r);
  */

    for (y=0; y<BUFF_HEIGHT; y++) {

      for (x=0; x<BUFF_WIDTH; x++) {
        closest=0;
        dist=9999999.0;

        temp_x=x;
        temp_y=y;
        for (i=0; i<nr_spirals; i++) {
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
          polar_a=atan2((double)(x-cent_x),(double)(y-cent_y));

          polar_r += (delta_r+(rnd.nextDouble()%10)*0.01)*(double)dir[closest];

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

            polar_a += (delta_a+(rnd.nextDouble()%10)*0.01)
              *(double)dir[closest];
          }

          temp_y=polar_r*(cos(polar_a));
          temp_x=polar_r*(sin(polar_a));

          map_x=(int)(temp_x+cent_x);
          map_y=(int)(temp_y+cent_y);

          if ((map_y>=BUFF_HEIGHT) || (map_y<0) ||
            (map_x>=BUFF_WIDTH) || (map_x<0) ) {
            map_x=0;
            map_y=0;
          }

          map_x=max(map_x,0);
          map_y=max(map_y,0);

          mapValue = map_y * BUFF_WIDTH + map_x;

        }
        map.writeShort(mapValue);
      }
    }
    return 0;
  }
}
