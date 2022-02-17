package io.github.duckasteroid.cthugha.wave;

import io.github.duckasteroid.cthugha.ScreenBuffer;
import io.github.duckasteroid.cthugha.audio.AudioBuffer;
import java.util.Random;
import java.util.stream.IntStream;

public class SpeckleWave implements Wave {
  public double fractionMax = 0.01;
  public double triggerPoint = 0.2;
  Random rnd = new Random();

  public SpeckleWave fraction(double f) {
    this.fractionMax = Math.max(1.0, f);
    return this;
  }

  @Override
  public void wave(AudioBuffer.AudioSample sound, ScreenBuffer buffer) {
    final int max = (int)(buffer.pixels.length * fractionMax);
    double intensity = sound.intensity(AudioBuffer.Channel.MONO_AVG); // FIXME use FFT DC (0Hz)
    double relativeIntensity =  intensity / Short.MAX_VALUE;
    if (relativeIntensity > triggerPoint) {
      IntStream.range(0, (int) (max * relativeIntensity))
        .forEach(i -> {
          int x = rnd.nextInt(buffer.width);
          int y = rnd.nextInt(buffer.height);
          buffer.pixels[buffer.index(x, y)] = (byte) 255;
        });
    }
  }
}
