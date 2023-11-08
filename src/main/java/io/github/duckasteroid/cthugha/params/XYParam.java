package io.github.duckasteroid.cthugha.params;

import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import java.awt.Dimension;
import java.awt.Point;
import java.util.Collection;
import java.util.List;
import java.util.function.DoublePredicate;
import java.util.stream.Stream;

public class XYParam extends AbstractNode {
  public final DoubleParameter x;
  public final DoubleParameter y;

  public XYParam(String name) {
    this(name, 0, 1, 0);
  }


  public XYParam(String name, double min, double max, double value) {
    super(name);
    this.x = new DoubleParameter("X", min, max, value);
    this.y = new DoubleParameter("Y", min, max, value);
    initChildren(x, y);
  }

  public boolean is(DoublePredicate test) {
    return Stream.of(x, y)
      .map(DoubleParameter::getValue)
      .mapToDouble(Number::doubleValue)
      .anyMatch(test);
  }

  public void setCenterOf(Dimension dims) {
    x.setValue(dims.width / 2);
    y.setValue(dims.height / 2);
  }

  public Point pixelLocation(Dimension d) {
    return new Point((int) (d.width * x.value), (int) (d.height * y.value));
  }
}
