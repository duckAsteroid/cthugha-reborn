package io.github.duckasteroid.cthugha.wave;

import io.github.duckasteroid.cthugha.ScreenBuffer;
import io.github.duckasteroid.cthugha.audio.AudioBuffer;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.Arrays;
import java.util.IntSummaryStatistics;

public class VibratingCircleWave implements Wave {
  private Stroke stroke = new BasicStroke(10f);
  @Override
  public void wave(AudioBuffer.AudioSample sound, ScreenBuffer buffer) {
    int x = buffer.width /2;
    int y = buffer.height /2;
    int min = 10;
    int max = buffer.height;
    IntSummaryStatistics intSummaryStatistics =
      Arrays.stream(sound.samples).mapToInt(s -> s[0]).summaryStatistics();
    double extent = intSummaryStatistics.getMax() / (double)Short.MAX_VALUE; // 0-1
    int range = max - min;
    int radius = (int)(min + (extent * range));
    Graphics2D graphics = buffer.getBufferedImageView().createGraphics();
    graphics.setColor(buffer.getForegroundColor());
    graphics.setStroke(stroke);
    graphics.drawOval(x - radius, y - radius, radius * 2, radius * 2);
    graphics.dispose();
  }


}
