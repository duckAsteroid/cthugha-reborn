package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.display.ScreenBuffer;
import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.EnumParameter;
import java.awt.Dimension;
import java.awt.Point;
import org.apache.commons.numbers.fraction.Fraction;

public class LinearSlider extends AbstractNode implements TranslateTableSource {
  public DoubleParameter focalPoint = new DoubleParameter("Relative focus", 0, 1, 0.3);
  public DoubleParameter speed = new DoubleParameter("Speed", -100, 100, 10);

  public BooleanParameter horizontal = new BooleanParameter("Horizontal", true);
  public BooleanParameter invertHalf = new BooleanParameter("Invert mode", true);

  public LinearSlider() {
    initChildren(focalPoint, speed, horizontal, invertHalf);
  }

  @Override
  public int[] generate(ScreenBuffer buffer) {
    final Dimension size = buffer.getDimensions();
    int[] result = new int[size.width * size.height];
    if (horizontal.value) {
      int focus = (int) (size.width * focalPoint.value);
      int mid = size.height / 2;
      for (int x = 0; x < buffer.width; x++) {
        int dist = Math.abs(x - focus); // how far from the focus
        Fraction distFraction = Fraction.of(dist, focus);
        double columnSpeed = distFraction.doubleValue() * speed.value;
        for (int y = 0; y < buffer.height; y++) {
          double srcY = (invertHalf.value && y > mid) ? y + columnSpeed : y - columnSpeed;
          Point target = new Point(x, (int) (srcY));
          result[buffer.index(x, y)] = buffer.toIndex(target, ScreenBuffer.PointWrapMode.WRAP);
        }
      }
    }
    else {
      int focus = (int) (size.height * focalPoint.value);
      int mid = size.width / 2;
      for (int y = 0; y < buffer.height; y++) {
        int dist = Math.abs(y - focus); // how far from the focus
        Fraction distFraction = Fraction.of(dist, focus);
        double columnSpeed = distFraction.doubleValue() * speed.value;
        for (int x = 0; x < buffer.width; x++) {
          double srcX = (invertHalf.value && x > mid) ? x + columnSpeed : x - columnSpeed;
          Point target = new Point((int)(srcX), y);
          result[buffer.index(x, y)] = buffer.toIndex(target, ScreenBuffer.PointWrapMode.WRAP);
        }
      }
    }
    return result;
  }

}
