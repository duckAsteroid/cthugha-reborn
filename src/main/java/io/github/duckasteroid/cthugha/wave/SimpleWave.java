package io.github.duckasteroid.cthugha.wave;

import io.github.duckasteroid.cthugha.ScreenBuffer;
import io.github.duckasteroid.cthugha.audio.AudioBuffer;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A simple rendering of the audio wave on the screen
 */
public class SimpleWave implements Wave {

  private double location = 0.5; // 0 - 1
  private double waveHeight = 1.0; // 1 = norm
  private double rotationAngle = 0.0;
  private Stroke stroke = new BasicStroke(2f);

  public SimpleWave wave(int size) {
    this.stroke = new BasicStroke(size);
    return this;
  }

  public SimpleWave location(double location) {
    this.location = location;
    return this;
  }

  public SimpleWave height(double waveHeight) {
    this.waveHeight = waveHeight;
    return this;
  }

  public SimpleWave rotate(double angle) {
    this.rotationAngle += angle;
    return this;
  }

  public void wave(AudioBuffer.AudioSample sound, ScreenBuffer buffer) {
    Graphics2D graphics = buffer.getGraphics();
    //graphics.clearRect(0,0, buffer.width, buffer.height);
    graphics.setColor(buffer.getForegroundColor());
    graphics.transform(AffineTransform.getRotateInstance(Math.toRadians(rotationAngle), buffer.width / 2, buffer.height / 2 ));
    int[] xs = IntStream.range(0, sound.samples.length).toArray();
    int[] ys = Arrays.stream(sound.samples)
      .mapToInt(sample -> (sample[0] + sample[1]) / 2)
      .map(sample -> Math.min(Short.MAX_VALUE, (int)(sample * waveHeight)))
      .map(sample -> (int)(buffer.height * location) + AudioBuffer.transpose((short)sample, (int)(buffer.height * (1 - location))))
      .toArray();
    graphics.setStroke(stroke);
    graphics.drawPolyline(xs, ys, xs.length);
    graphics.dispose();
  }

  public void wave3(AudioBuffer.AudioSample sound, ScreenBuffer buffer) {
    Arrays.fill(buffer.pixels, (byte) 0);
    //graphics.transform(AffineTransform.getRotateInstance(Math.toRadians(rotationAngle)));
    int[] xs = IntStream.range(0, sound.samples.length).toArray();
    int[] ys = Arrays.stream(sound.samples)
      .mapToInt(sample -> sample[0])
      .map(sample -> (buffer.height / 2 ) + AudioBuffer.transpose((short)sample, buffer.height / 2))
      .toArray();
    for (int i= 0; i < ys.length; i++) {
      buffer.pixels[buffer.index(xs[i], ys[i])] = (byte)0xFF;
    }
  }


}
