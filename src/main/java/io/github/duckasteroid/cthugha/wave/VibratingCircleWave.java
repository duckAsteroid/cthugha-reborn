package io.github.duckasteroid.cthugha.wave;

import io.github.duckasteroid.cthugha.display.ScreenBuffer;
import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.audio.Channel;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;
import io.github.duckasteroid.cthugha.params.values.StrokeParameter;
import io.github.duckasteroid.cthugha.params.values.XYParam;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;

/**
 * A circle that is drawn
 */
public class VibratingCircleWave implements Wave {
  public XYParam location = new XYParam("Location");
  public IntegerParameter frequency = new IntegerParameter("Frequency", 0, 512, 0);
  public DoubleParameter maximum = new DoubleParameter("Max radius", 0, 2);
  public DoubleParameter minimum = new DoubleParameter("Min radius", 0, 1);

  public BooleanParameter fill = new BooleanParameter("Fill the circle?");
  private StrokeParameter stroke = new StrokeParameter(20);
  @Override
  public void wave(AudioSample sound, ScreenBuffer buffer) {
    int min = (int)(minimum.value * buffer.width);
    Point locationPoint = location.pixelLocation(buffer.getDimensions());
    double extent = sound.fft().getMagnitude(frequency.value);
    int range = (int)((maximum.value * buffer.width) - min);
    int radius = (int)(min + (extent * range));
    Graphics2D graphics = buffer.getBufferedImageView().createGraphics();
    graphics.setColor(buffer.getForegroundColor());
    graphics.setStroke(stroke.getStroke());
    if (fill.value) {
      graphics.fillOval(locationPoint.x - radius, locationPoint.y - radius, radius * 2, radius * 2);
    }
    else {
      graphics.drawOval(locationPoint.x - radius, locationPoint.y - radius, radius * 2, radius * 2);
    }
    graphics.dispose();
  }


}
