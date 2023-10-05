package io.github.duckasteroid.cthugha.params;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class AffineTransformParams implements Parameterized {
  public static final double MIN = Double.MIN_VALUE;
  public static final double MAX = Double.MAX_VALUE;
  private final String name;
  public final XYParam scale = new XYParam("Scale",MIN, MAX, 1.0);
  public final XYParam shear = new XYParam("Shear", MIN, MAX, 0.0);
  public final XYParam translate = new XYParam("Translation", MIN, MAX, 0.0);
  public final DoubleParameter rotate = new DoubleParameter("Rotation in radians", 0, 2 * Math.PI);

  public final XYParam rotateCenter = new XYParam("Rotation center point", MIN, MAX, 0);
  public AffineTransformParams(String name) {
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Collection<RuntimeParameter<?>> params() {
    ArrayList<RuntimeParameter<?>> result = new ArrayList<>(7);
    result.addAll(scale.params());
    result.addAll(shear.params());
    result.addAll(translate.params());
    result.add(rotate);
    result.addAll(rotateCenter.params());
    return result;
  }

  private static Predicate<Double> equals(final double expected, final int dp) {
    final double factor = Math.pow(10, dp);
    return (actual) -> Math.round(actual * factor) == Math.round(expected * factor);
  }

  public final Predicate<Double> UNITY = equals(1.0, 10);
  public final Predicate<Double> ZERO = equals(0.0, 10);

  public AffineTransform applyTo(AffineTransform transform) {
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
      transform.rotate(rotate.value, rotateCenter.x.value, rotateCenter.y.value);
    }
    return transform;
  }

  public static class XYParam implements Parameterized {
    private final String name;
    public final DoubleParameter x;
    public final DoubleParameter y;

    public XYParam(String name) {
      this(name, 0, 1, 0);
    }


    public XYParam(String name, double min, double max, double value) {
      this.name = name;
      this.x = new DoubleParameter("X", min, max, value);
      this.y = new DoubleParameter("Y", min, max, value);
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public Collection<RuntimeParameter<?>> params() {
      return List.of(x, y);
    }

    public boolean is(Predicate<Double> test) {
      return Stream.of(x, y).map(DoubleParameter::getValue).anyMatch(test);
    }

  }
}
