package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import org.apache.commons.numbers.fraction.Fraction;

public class LinearSlider extends AbstractNode implements TranslateTableSource {
  public DoubleParameter focalPoint = new DoubleParameter("Relative focus", 0, 1, 0.3);
  public DoubleParameter speed = new DoubleParameter("Speed", -100, 100, 10);

  public BooleanParameter horizontal = new BooleanParameter("Horizontal", true);

  public LinearSlider() {
    initChildren(focalPoint, speed);
  }

  @Override
  public int[] generate(int width, int height) {
    int[] result = new int[width * height];
    int focus = (int)(width * focalPoint.value);
    for (int x = 0; x < width; x++) {
      int dist = Math.abs(x - focus);
      Fraction distFraction = Fraction.of(dist, focus);
      double columnSpeed = distFraction.doubleValue() * speed.value;
      for (int y = 0; y < height; y++) {
        int targetX = x;
        int targetY = (int)(y - columnSpeed);
        result[TranslateTableSource.index(x, y, width)] =
            TranslateTableSource.index(
                TranslateTableSource.wrap(targetX, width),
                TranslateTableSource.wrap(targetY, height),
                width);
      }
    }
    return result;
  }
}
