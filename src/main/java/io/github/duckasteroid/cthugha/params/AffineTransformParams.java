package io.github.duckasteroid.cthugha.params;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.DoublePredicate;

public class AffineTransformParams implements Parameterized {
  public static final double MIN = Double.MIN_VALUE;
  public static final double MAX = Double.MAX_VALUE;
  private final String name;
  public final XYParam scale = new XYParam("Scale",MIN, MAX, 1.0);
  public final XYParam shear = new XYParam("Shear", MIN, MAX, 0.0);
  public final XYParam translate = new XYParam("Translation", MIN, MAX, 0.0);
  public final DoubleParameter rotate = new DoubleParameter("Rotation in radians", 0, 2 * Math.PI);

  public final XYParam rotateCenter = new XYParam("Rotation center point", 0, 1, 0);
  public AffineTransformParams(String name) {
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Collection<RuntimeParameter> params() {
    ArrayList<RuntimeParameter> result = new ArrayList<>(7);
    result.addAll(scale.params());
    result.addAll(shear.params());
    result.addAll(translate.params());
    result.add(rotate);
    result.addAll(rotateCenter.params());
    return result;
  }

  private static DoublePredicate equals(final double expected, final int dp) {
    final double factor = Math.pow(10, dp);
    return (actual) -> Math.round(actual * factor) == Math.round(expected * factor);
  }

  public final DoublePredicate UNITY = equals(1.0, 10);
  public final DoublePredicate ZERO = equals(0.0, 10);

  public AffineTransform applyTo(Dimension dim, AffineTransform transform) {
    if (!scale.is(UNITY)) {
      transform.scale(scale.x.value, scale.y.value);
    }
    if (!translate.is(ZERO)) {
      transform.translate(translate.x.value, translate.y.value);
    }
    if (!shear.is(ZERO)) {
      transform.shear(shear.x.value, shear.y.value);
    }
    if (rotate.value % 2 * Math.PI != 0.0) {
      Point rotateCenterPoint = rotateCenter.pixelLocation(dim);
      transform.rotate(rotate.value, rotateCenterPoint.x, rotateCenterPoint.y);
    }
    return transform;
  }

}
