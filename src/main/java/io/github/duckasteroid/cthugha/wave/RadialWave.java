package io.github.duckasteroid.cthugha.wave;

import io.github.duckasteroid.cthugha.ScreenBuffer;
import io.github.duckasteroid.cthugha.audio.AudioBuffer;
import io.github.duckasteroid.cthugha.audio.AudioPoint;
import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.audio.Channel;
import io.github.duckasteroid.cthugha.params.IntegerParameter;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.List;

public class RadialWave implements Wave {
  private IntegerParameter strokeWidth = new IntegerParameter("Stroke width", 1, 24, 12);

  private static final int radialSamples = 200;
  int[] xs = new int[radialSamples];
  int[] ys = new int[radialSamples];
  private Stroke stroke;

  public RadialWave() {
    wave(12);
  }

  public RadialWave wave(int size) {
    this.strokeWidth.value = size;
    this.stroke = new BasicStroke(size);
    return this;
  }

  @Override
  public void wave(AudioSample sound, ScreenBuffer buffer) {
    if (sound.size() >= radialSamples) {
      int skipFactor = (int) Math.ceil((double) sound.size() / (double) radialSamples);
      final double angle = 2 * Math.PI / radialSamples;

      final int maxRadius = Math.min(buffer.width, buffer.height) / 2;
      List<AudioPoint> samples = sound.streamPoints().toList();
      for (int i = 0; i < radialSamples; i++) {
        int soundIndex = Math.max(0, Math.min(sound.size() - 1, i * skipFactor));
        int delta = (int) samples.get(soundIndex).normalised(Channel.MONO_AVG) * maxRadius;
        int radius = (maxRadius / 2) + delta;
        xs[i] = (int) (radius * Math.cos(i * angle)) + (buffer.width / 2);
        ys[i] = (int) (radius * Math.sin(i * angle)) + (buffer.height / 2);
      }
      Graphics2D graphics = buffer.getBufferedImageView().createGraphics();
      graphics.setColor(buffer.getForegroundColor());
      graphics.setStroke(stroke);
      graphics.drawPolyline(xs, ys, xs.length);
      graphics.dispose();
    }
  }
}
