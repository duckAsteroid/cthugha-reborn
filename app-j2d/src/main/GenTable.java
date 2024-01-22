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

    
    return 0;
  }
}
