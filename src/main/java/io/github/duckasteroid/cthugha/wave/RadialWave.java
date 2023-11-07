package io.github.duckasteroid.cthugha.wave;

import io.github.duckasteroid.cthugha.ScreenBuffer;
import io.github.duckasteroid.cthugha.audio.AudioBuffer;
import io.github.duckasteroid.cthugha.audio.AudioPoint;
import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.audio.Channel;
import io.github.duckasteroid.cthugha.params.IntegerParameter;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RadialWave implements Wave {
  private IntegerParameter strokeWidth = new IntegerParameter("Stroke width", 1, 24, 12);

  private static final int radialSamples = 200;
  private Stroke stroke;

  public RadialWave() {
    wave(12);
  }

  public RadialWave wave(int size) {
    this.strokeWidth.value = size;
    this.stroke = new BasicStroke(size);
    return this;
  }

  public Polyline calculateWaveform(AudioSample sound, ScreenBuffer buffer) {
    if (sound.size() >= radialSamples) {
      // the angle of each radial point around the circle
      final double angle = 2 * Math.PI / radialSamples;
      // max radius is half the smaller of width or height
      final int maxRadius = Math.min(buffer.width, buffer.height) / 2;
      // get the full audio sample as a list
      return Polyline.wrap(sound.downsample(radialSamples).map(audio -> {
          int delta = AudioPoint.transpose(audio.value(Channel.MONO_AVG),  maxRadius);
          int radius = (maxRadius / 2) + delta;
          int index = audio.index;
          int x = (int) (radius * Math.cos(index * angle)) + (buffer.width / 2);
          int y = (int) (radius * Math.sin(index * angle)) + (buffer.height / 2);
          return new Point(x, y);
        }));
    }
    return new Polyline(0);
  }

  @Override
  public void wave(AudioSample sound, ScreenBuffer buffer) {
    Polyline line = calculateWaveform(sound, buffer);
    if (!line.isEmpty()) {
      Graphics2D graphics = buffer.getBufferedImageView().createGraphics();
      graphics.setColor(buffer.getForegroundColor());
      graphics.setStroke(stroke);
      graphics.drawPolyline(line.xs, line.ys, line.size());
      graphics.dispose();
    }
  }
}
