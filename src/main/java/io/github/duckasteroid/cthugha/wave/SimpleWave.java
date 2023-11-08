package io.github.duckasteroid.cthugha.wave;

import io.github.duckasteroid.cthugha.display.ScreenBuffer;
import io.github.duckasteroid.cthugha.audio.AudioPoint;
import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.audio.Channel;
import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.AffineTransformParams;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;

/**
 * A simple rendering of the audio wave on the screen
 */
public class SimpleWave extends AbstractNode implements Wave {

  public final DoubleParameter location = new DoubleParameter("Relative screen location of the wave as a fraction of height", 0,1, 0.5); // 0 - 1
  public final DoubleParameter waveHeight = new DoubleParameter("wave height",0,1,1.0); // 1 = norm

  public final AffineTransformParams transformParams = new AffineTransformParams("Wave transform");

  public final DoubleParameter strokeWidth = new DoubleParameter("Stroke width", 1.0, 24.0, 2.0);

  public SimpleWave() {
    super("SimpleWave");
    initFields(SimpleWave.class);
  }

  public SimpleWave wave(int size) {
    this.strokeWidth.setValue(size); ;
    return this;
  }

  public SimpleWave location(double location) {
    this.location.value = location;
    return this;
  }

  public SimpleWave height(double waveHeight) {
    this.waveHeight.value = waveHeight;
    return this;
  }

  public SimpleWave rotate(double angle) {
    this.transformParams.rotate.value += Math.toRadians(angle);
    return this;
  }

  public SimpleWave autoRotate(double delta) {
    // FIXME use animations!
    return this;
  }

  public void wave(final AudioSample sound, final ScreenBuffer buffer) {
    Graphics2D graphics = buffer.getGraphics();
    //graphics.clearRect(0,0, buffer.width, buffer.height);
    graphics.setColor(buffer.getForegroundColor());
    AffineTransform transform = transformParams.applyTo(buffer.getDimensions(), graphics.getTransform());
    graphics.transform(transform);
    final double realLocation = location.value / 2;

    int[] xs = new int[sound.size()];
    int[] y1s = new int[sound.size()];
    int[] y2s = new int[sound.size()];
    // copy data from sample into X/Y arrays
    sound.streamPoints()
      .map(sample -> screenYs(buffer, realLocation, location.value, waveHeight.value, sample))
      .forEach(point -> {
        int index = point[0];
        xs[index] = index;
        y1s[index] = point[1];
        y2s[index] = point[2];
      });

    Stroke stroke = new BasicStroke((float) strokeWidth.value);
    graphics.setStroke(stroke);
    graphics.drawPolyline(xs, y1s, xs.length);
    graphics.drawPolyline(xs, y2s, xs.length);
    graphics.dispose();
  }


  // 3 D array of X, Y1 and Y2
  public static int[] screenYs(final ScreenBuffer buffer, double realLocation, double location, double waveHeight, AudioPoint sample) {

    return new int[]{
      sample.index,
      (int)(buffer.height * realLocation) +
        AudioPoint.transpose(
          (short) Math.min(Short.MAX_VALUE, sample.value(Channel.LEFT) * waveHeight),
          (int)(buffer.height * (location))),
      (int)(buffer.height * (1 - realLocation)) +
        AudioPoint.transpose(
          (short) Math.min(Short.MAX_VALUE, sample.value(Channel.RIGHT) * waveHeight),
          (int)(buffer.height * (1 - location)))
      };
  }

}
