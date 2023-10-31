package io.github.duckasteroid.cthugha.wave;

import io.github.duckasteroid.cthugha.ScreenBuffer;
import io.github.duckasteroid.cthugha.audio.AudioBuffer;
import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.params.IntegerParameter;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Stroke;

public class RadialWave implements Wave {
  private IntegerParameter strokeWidth = new IntegerParameter("Stroke width", 1, 24, 12);

  public IntegerParameter radialSamples = new IntegerParameter("Radial samples", 0, 360, 200);
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
    if (sound.samples.length >= radialSamples.value) {
      int skipFactor = (int) Math.ceil((double) sound.samples.length / (double) radialSamples.value);
      final double angle = 2 * Math.PI / radialSamples.value;
      int[] xs = new int[radialSamples.value];
      int[] ys = new int[radialSamples.value];
      final int maxRadius = Math.min(buffer.width, buffer.height) / 2;
      for (int i = 0; i < radialSamples.value; i++) {
        int soundIndex = Math.max(0, Math.min(sound.samples.length - 1, i * skipFactor));
        int delta = (int) (AudioBuffer.normalise(sound.samples[soundIndex][0]) * maxRadius);
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
