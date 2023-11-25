package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.display.ScreenBuffer;
import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import java.awt.Dimension;
import java.awt.Point;
import org.apache.commons.numbers.fraction.Fraction;

public class LinearSlider extends AbstractNode implements TranslateTableSource {
  public DoubleParameter focalPoint = new DoubleParameter("Relative focus", 0, 1, 0.3);
  public DoubleParameter speed = new DoubleParameter("Speed", -100, 100, 10);

  public BooleanParameter horizontal = new BooleanParameter("Horizontal", true);

  public LinearSlider() {
    initChildren(focalPoint, speed);
  }

  @Override
  public int[] generate(ScreenBuffer buffer) {
    final Dimension size = buffer.getDimensions();
    int[] result = new int[size.width * size.height];
    int focus = (int)(size.width * focalPoint.value);
    int mid = size.height / 2;
    for(int x = 0; x < buffer.width; x++) {
      int dist = Math.abs(x - focus); // how far from the focus
      Fraction distFraction = Fraction.of(dist , focus);
      double columnSpeed = distFraction.doubleValue() * speed.value;
      for(int y = 0; y < buffer.height; y++) {
        Point target = new Point(x, (int) (y - columnSpeed));
        result[buffer.index(x,y)] = buffer.toIndex(target, ScreenBuffer.PointWrapMode.WRAP);
      }
    }
    return result;
  }

}
