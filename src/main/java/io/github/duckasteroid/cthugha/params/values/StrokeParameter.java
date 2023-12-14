package io.github.duckasteroid.cthugha.params.values;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Point;
import java.util.List;
import java.util.function.DoublePredicate;
import java.util.stream.Stream;

/**
 * Represents parameters for an AWT stroke (line) style
 */
public class StrokeParameter extends AbstractNode {
  private FloatParameter width = new FloatParameter("Width", 0.1f, 20, 2);
  private EnumParameter<String> capStyle = new EnumParameter<>("Cap style", List.of("BUTT", "ROUND", "SQUARE"));

  public StrokeParameter(int w) {
    super("Stroke");
    width.setValue(w);
    initChildren(width, capStyle);
  }

  public BasicStroke getStroke() {
    return new BasicStroke(width.value, capStyle.value, BasicStroke.JOIN_ROUND);
  }

}
