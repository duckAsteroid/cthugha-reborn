package io.github.duckasteroid.cthugha.wave;

import io.github.duckasteroid.cthugha.ScreenBuffer;
import io.github.duckasteroid.cthugha.audio.AudioBuffer;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Stroke;

public class RadialWave implements Wave {
  private Stroke stroke = new BasicStroke(10f);
  private int radialSamples = 200;

  @Override
  public void wave(AudioBuffer.AudioSample sound, ScreenBuffer buffer) {
    if (sound.samples.length >= radialSamples) {
      int skipFactor = (int) Math.ceil((double) sound.samples.length / (double) radialSamples);
      final double angle = 2 * Math.PI / radialSamples;
      int[] xs = new int[radialSamples];
      int[] ys = new int[radialSamples];
      final int maxRadius = Math.min(buffer.width, buffer.height) / 2;
      for (int i = 0; i < radialSamples; i++) {
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
