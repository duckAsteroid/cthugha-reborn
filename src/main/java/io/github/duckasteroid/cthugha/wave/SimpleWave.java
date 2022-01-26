package io.github.duckasteroid.cthugha.wave;

import io.github.duckasteroid.cthugha.ScreenBuffer;
import java.util.Arrays;

/**
 * A simple rendering of the audio wave on the screen
 */
public class SimpleWave implements Wave {
  private int[] wave = new int[]{255};
  private double location = 0.5; // 0 - 1
  private double waveHeight = 0.2; // 1 = norm

  public SimpleWave wave(int size) {
    this.wave = new int[size];
    Arrays.fill(this.wave, 255);
    return this;
  }

  public SimpleWave wave(int ... wavePixels) {
    this.wave = wavePixels;
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

  public SimpleWave rotate(double speed) {
    return this;
  }

  @Override
  public void wave(int[] sound, ScreenBuffer buffer) {
    int centreLine = (int) (buffer.height * location);

    // wave
    for (int x = 0; x < buffer.width; x++) {
      for (int w = 0; w < wave.length; w++) {
        int y = centreLine + w + (int) (sound[x] * waveHeight);
        buffer.pixels[buffer.index(x, y)] = (byte) wave[w];
      }
    }

    int dx, dy, x, y, k;
    int x1 = 50, y1 = 50, x2 = 200, y2 = 180;

    dx = x2 - x1;
    dy = y2 - y1;

    int p0 = 2 * dy - dx, p1;

    x = x1;
    y = y1;

    for (k = 1; k <= dx; k++) {
      if (p0 < 0) {
        p1 = p0 + (2 * dy);
        x++;
      } else {
        p1 = p0 + (2 * dy) - (2 * dx);
        x++;
        y++;
      }
      //raster.setPixel(x, y, array);
    }
  }
}
