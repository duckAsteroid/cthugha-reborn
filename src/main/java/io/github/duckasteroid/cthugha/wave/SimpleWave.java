package io.github.duckasteroid.cthugha.wave;

import io.github.duckasteroid.cthugha.ScreenBuffer;
import io.github.duckasteroid.cthugha.audio.AudioBuffer;
import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.params.AffineTransformParams;
import io.github.duckasteroid.cthugha.params.BooleanParameter;
import io.github.duckasteroid.cthugha.params.Parameterized;
import io.github.duckasteroid.cthugha.params.DoubleParameter;
import io.github.duckasteroid.cthugha.params.RuntimeParameter;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;

/**
 * A simple rendering of the audio wave on the screen
 */
public class SimpleWave implements Wave, Parameterized {

  private final DoubleParameter location = new DoubleParameter("Location of the wave", 0,1, 0.5); // 0 - 1
  private final DoubleParameter waveHeight = new DoubleParameter("wave height",0,1,1.0); // 1 = norm

  private final AffineTransformParams transformParams = new AffineTransformParams("Simple wave");
  private final BooleanParameter stereo = new BooleanParameter("Stereo", true);

  private Stroke stroke = new BasicStroke(2f);

  public SimpleWave wave(int size) {
    this.stroke = new BasicStroke(size);
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
    // use animations!
    return this;
  }

  @Override
  public String getName() {
    return "Simple wave";
  }

  @Override
  public Collection<RuntimeParameter<?>> params() {
    return List.of(location, waveHeight, stereo);
  }

  public void wave(AudioSample sound, ScreenBuffer buffer) {
    Graphics2D graphics = buffer.getGraphics();
    //graphics.clearRect(0,0, buffer.width, buffer.height);
    graphics.setColor(buffer.getForegroundColor());
    AffineTransform transform = transformParams.applyTo(graphics.getTransform());

    graphics.transform(transform);
    int[] xs = IntStream.range(0, sound.samples.length).toArray();
    final double realLocation = stereo.value ? location.value / 2 : location.value;
    int[] y1s = Arrays.stream(sound.samples)
      .mapToInt(sample -> sample[0])
      .map(sample -> Math.min(Short.MAX_VALUE, (int)(sample * waveHeight.value)))
      .map(sample -> (int)(buffer.height * realLocation) +
        AudioBuffer.transpose((short)sample, (int)(buffer.height * (1 - location.value))))
      .toArray();
    graphics.setStroke(stroke);
    graphics.drawPolyline(xs, y1s, xs.length);
    if (stereo.value) {
      int[] y2s = Arrays.stream(sound.samples)
        .mapToInt(sample -> sample[1])
        .map(sample -> Math.min(Short.MAX_VALUE, (int) (sample * waveHeight.value)))
        .map(sample -> (int) (buffer.height * (1-realLocation)) +
          AudioBuffer.transpose((short) sample, (int) (buffer.height * (1 - location.value))))
        .toArray();
      graphics.drawPolyline(xs, y2s, xs.length);
    }
    graphics.dispose();
  }

  public void wave3(AudioSample sound, ScreenBuffer buffer) {
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
