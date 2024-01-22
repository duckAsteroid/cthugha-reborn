package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.display.ScreenBuffer;
import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;
import java.awt.Dimension;
import java.awt.Point;

public class Wavy extends AbstractNode implements TranslateTableSource {
  public IntegerParameter yAmp = new IntegerParameter("Amplitude", 1, 20);
  public DoubleParameter ySpeed = new DoubleParameter("Speed", -3, 3);

  public IntegerParameter xAmp = new IntegerParameter("Amplitude", 1, 20);
  public DoubleParameter xSpeed = new DoubleParameter("Speed", -3, 3);
  public Wavy() {
    super("Wavy");
    initChildren(yAmp, ySpeed, xAmp, xSpeed);
  }
  @Override
  public int[] generate(ScreenBuffer buffer) {
    final Dimension size = buffer.getDimensions();
    int[] theTab = new int[size.height * size.width];
    for(int y=0; y < buffer.height; y++) {
      for (int x=0; x < buffer.width; x++) {
        Point pt = new Point(x, y);
        int index = buffer.toIndex(pt, ScreenBuffer.PointWrapMode.WRAP);
        double srcY = y - ySpeed.value;
        double srcX = x - xSpeed.value;
        int x2 = x + (int) (yAmp.value * Math.sin(Math.toRadians(srcY)));
        int y2 = y + (int) (xAmp.value * Math.sin(Math.toRadians(srcX)));
        theTab[index] = buffer.toIndex(new Point(x2, y2), ScreenBuffer.PointWrapMode.WRAP);
      }
    }
    return theTab;
  }
}
